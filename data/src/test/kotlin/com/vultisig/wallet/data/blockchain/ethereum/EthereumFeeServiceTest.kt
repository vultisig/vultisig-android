@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_ARBITRUM_TRANSFER
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_COIN_TRANSFER_LIMIT
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_MANTLE_SWAP_LIMIT
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_SWAP_LIMIT
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_TOKEN_TRANSFER_LIMIT
import com.vultisig.wallet.data.blockchain.model.Eip1559
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Swap
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class EthereumFeeServiceTest {

    private val evmApi: EvmApi = mockk()
    private val evmApiFactory: EvmApiFactory = mockk()
    private val service = EthereumFeeService(evmApiFactory)

    @BeforeEach
    fun setUp() {
        every { evmApiFactory.createEvmApi(any()) } returns evmApi
        coEvery { evmApi.getBaseFee() } returns BigInteger.ZERO
        coEvery { evmApi.getFeeHistory() } returns emptyList()
    }

    // ---------- Bug #6 regression: empty fee history must not crash ----------

    @Test
    fun `Polygon with empty fee history falls back to the 30 GWEI floor`() = runTest {
        val fee = service.calculateDefaultFees(transfer(Chain.Polygon)) as Eip1559

        assertEquals(POLYGON_MIN_TIP, fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Ethereum with empty fee history falls back to the 1 GWEI floor`() = runTest {
        val fee = service.calculateDefaultFees(transfer(Chain.Ethereum)) as Eip1559

        assertEquals(GWEI, fee.maxPriorityFeePerGas)
    }

    @Test
    fun `non-polygon EVM chain with empty fee history falls back to the 1 GWEI floor`() = runTest {
        val fee = service.calculateDefaultFees(transfer(Chain.ZkSync)) as Eip1559

        assertEquals(GWEI, fee.maxPriorityFeePerGas)
    }

    // ---------- Per-chain priority-fee logic ----------

    @Test
    fun `Avalanche uses getMaxPriorityFeePerGas and ignores fee history`() = runTest {
        stubFeeHistory(listOf(gwei(50), gwei(100), gwei(200)))
        coEvery { evmApi.getMaxPriorityFeePerGas() } returns gwei(2)

        val fee = service.calculateDefaultFees(transfer(Chain.Avalanche)) as Eip1559

        assertEquals(gwei(2), fee.maxPriorityFeePerGas)
        coVerify(exactly = 1) { evmApi.getMaxPriorityFeePerGas() }
    }

    @Test
    fun `Arbitrum priority fee is always zero`() = runTest {
        stubFeeHistory(listOf(gwei(5), gwei(10)))

        val fee = service.calculateDefaultFees(transfer(Chain.Arbitrum)) as Eip1559

        assertEquals(BigInteger.ZERO, fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Mantle priority fee is always zero`() = runTest {
        stubFeeHistory(listOf(gwei(5)))

        val fee = service.calculateDefaultFees(transfer(Chain.Mantle)) as Eip1559

        assertEquals(BigInteger.ZERO, fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Blast with empty history uses the 001 GWEI floor`() = runTest {
        val fee = service.calculateDefaultFees(transfer(Chain.Blast)) as Eip1559

        assertEquals(BLAST_MIN_TIP, fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Blast with rewards below the floor still uses the floor`() = runTest {
        stubFeeHistory(listOf(BigInteger.ONE, BigInteger("100")))

        val fee = service.calculateDefaultFees(transfer(Chain.Blast)) as Eip1559

        assertEquals(BLAST_MIN_TIP, fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Blast uses max reward when it exceeds the floor`() = runTest {
        stubFeeHistory(listOf(gwei(1), gwei(3), gwei(2)))

        val fee = service.calculateDefaultFees(transfer(Chain.Blast)) as Eip1559

        assertEquals(gwei(3), fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Base with empty history uses the L2 default`() = runTest {
        val fee = service.calculateDefaultFees(transfer(Chain.Base)) as Eip1559

        assertEquals(L2_DEFAULT_TIP, fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Base uses max reward when history is populated`() = runTest {
        stubFeeHistory(listOf(gwei(50), gwei(200), gwei(100)))

        val fee = service.calculateDefaultFees(transfer(Chain.Base)) as Eip1559

        assertEquals(gwei(200), fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Optimism with empty history uses the L2 default`() = runTest {
        val fee = service.calculateDefaultFees(transfer(Chain.Optimism)) as Eip1559

        assertEquals(L2_DEFAULT_TIP, fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Optimism uses max reward when history is populated`() = runTest {
        stubFeeHistory(listOf(gwei(7), gwei(4)))

        val fee = service.calculateDefaultFees(transfer(Chain.Optimism)) as Eip1559

        assertEquals(gwei(7), fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Polygon uses the median reward when it exceeds the 30 GWEI floor`() = runTest {
        // odd-length list: middle index = size/2 = 2 → 100 GWEI
        stubFeeHistory(listOf(gwei(40), gwei(60), gwei(100), gwei(120), gwei(140)))

        val fee = service.calculateDefaultFees(transfer(Chain.Polygon)) as Eip1559

        assertEquals(gwei(100), fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Polygon uses the 30 GWEI floor when the median is below it`() = runTest {
        stubFeeHistory(listOf(gwei(1), gwei(2), gwei(3)))

        val fee = service.calculateDefaultFees(transfer(Chain.Polygon)) as Eip1559

        assertEquals(POLYGON_MIN_TIP, fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Ethereum uses the median reward when it exceeds 1 GWEI`() = runTest {
        stubFeeHistory(listOf(gwei(2), gwei(4), gwei(6)))

        val fee = service.calculateDefaultFees(transfer(Chain.Ethereum)) as Eip1559

        assertEquals(gwei(4), fee.maxPriorityFeePerGas)
    }

    @Test
    fun `Ethereum uses the 1 GWEI floor when the median is below it`() = runTest {
        stubFeeHistory(listOf(BigInteger.ONE, BigInteger.TEN, BigInteger.valueOf(100)))

        val fee = service.calculateDefaultFees(transfer(Chain.Ethereum)) as Eip1559

        assertEquals(GWEI, fee.maxPriorityFeePerGas)
    }

    // ---------- Median edge cases ----------

    @Test
    fun `single-element fee history returns that element as the median`() = runTest {
        stubFeeHistory(listOf(gwei(5)))

        val fee = service.calculateDefaultFees(transfer(Chain.Ethereum)) as Eip1559

        assertEquals(gwei(5), fee.maxPriorityFeePerGas)
    }

    @Test
    fun `two-element fee history picks the upper midpoint`() = runTest {
        // size/2 = 1 → second element
        stubFeeHistory(listOf(gwei(2), gwei(8)))

        val fee = service.calculateDefaultFees(transfer(Chain.Ethereum)) as Eip1559

        assertEquals(gwei(8), fee.maxPriorityFeePerGas)
    }

    // ---------- EIP-1559 fee assembly ----------

    @Test
    fun `maxFeePerGas equals baseFee increased by 20 percent plus priority fee`() = runTest {
        coEvery { evmApi.getBaseFee() } returns gwei(100)
        stubFeeHistory(listOf(gwei(5)))

        val fee = service.calculateDefaultFees(transfer(Chain.Ethereum)) as Eip1559

        assertEquals(gwei(100), fee.networkPrice)
        assertEquals(gwei(5), fee.maxPriorityFeePerGas)
        assertEquals(gwei(125), fee.maxFeePerGas) // 100 * 1.2 + 5
    }

    @Test
    fun `amount equals maxFeePerGas times limit`() = runTest {
        coEvery { evmApi.getBaseFee() } returns gwei(100)
        stubFeeHistory(listOf(gwei(5)))

        val fee = service.calculateDefaultFees(transfer(Chain.Ethereum)) as Eip1559

        assertEquals(fee.maxFeePerGas.multiply(fee.limit), fee.amount)
    }

    // ---------- Legacy gas path (BSC) ----------

    @Test
    fun `BSC uses legacy gas pricing`() = runTest {
        coEvery { evmApi.getGasPrice() } returns gwei(3)

        val fee = service.calculateDefaultFees(transfer(Chain.BscChain))

        assertTrue(fee is GasFees)
        assertEquals(gwei(3), fee.price)
        assertEquals(fee.price.multiply(fee.limit), fee.amount)
    }

    // ---------- Default limits ----------

    @Test
    fun `default limit for native transfer is the coin-transfer default`() = runTest {
        stubFeeHistory(listOf(gwei(2)))

        val fee = service.calculateDefaultFees(transfer(Chain.Ethereum)) as Eip1559

        assertEquals(DEFAULT_COIN_TRANSFER_LIMIT, fee.limit)
    }

    @Test
    fun `default limit for ERC-20 transfer is token-transfer default increased by 40 percent`() =
        runTest {
            stubFeeHistory(listOf(gwei(2)))

            val fee =
                service.calculateDefaultFees(transfer(Chain.Ethereum, isNative = false)) as Eip1559

            val expected = DEFAULT_TOKEN_TRANSFER_LIMIT.multiply(BigInteger("140")).divide(HUNDRED)
            assertEquals(expected, fee.limit)
        }

    @Test
    fun `default limit for Arbitrum transfer is the Arbitrum default`() = runTest {
        val fee = service.calculateDefaultFees(transfer(Chain.Arbitrum)) as Eip1559

        assertEquals(DEFAULT_ARBITRUM_TRANSFER, fee.limit)
    }

    @Test
    fun `default limit for swap is the swap default`() = runTest {
        stubFeeHistory(listOf(gwei(2)))

        val fee = service.calculateDefaultFees(swap(Chain.Ethereum)) as Eip1559

        assertEquals(DEFAULT_SWAP_LIMIT, fee.limit)
    }

    // ---------- calculateFees (uses on-chain gas estimation) ----------

    @Test
    fun `calculateFees uses estimated gas for native transfer when larger than default`() =
        runTest {
            val estimate = DEFAULT_COIN_TRANSFER_LIMIT.multiply(BigInteger("2"))
            coEvery { evmApi.estimateGasForEthTransaction(any(), any(), any(), any()) } returns
                estimate
            stubFeeHistory(listOf(gwei(2)))

            val fee = service.calculateFees(transfer(Chain.Ethereum)) as Eip1559

            assertEquals(estimate, fee.limit)
        }

    @Test
    fun `calculateFees never returns a limit below the chain default`() = runTest {
        coEvery { evmApi.estimateGasForEthTransaction(any(), any(), any(), any()) } returns
            BigInteger("100")
        stubFeeHistory(listOf(gwei(2)))

        val fee = service.calculateFees(transfer(Chain.Ethereum)) as Eip1559

        assertEquals(DEFAULT_COIN_TRANSFER_LIMIT, fee.limit)
    }

    @Test
    fun `calculateFees applies 50 percent safety margin to ERC-20 gas estimates`() = runTest {
        val estimate = BigInteger("200000")
        coEvery { evmApi.estimateGasForERC20Transfer(any(), any(), any(), any()) } returns estimate
        stubFeeHistory(listOf(gwei(2)))

        val fee = service.calculateFees(transfer(Chain.Ethereum, isNative = false)) as Eip1559

        // 200000 * 1.5 = 300000 which is above the 150000 * 1.4 default
        val expected = estimate.multiply(BigInteger("150")).divide(HUNDRED)
        assertEquals(expected, fee.limit)
    }

    // ---------- Error paths ----------

    @Test
    fun `calculateFees uses default-fee path for swap transactions`() = runTest {
        coEvery { evmApi.getBaseFee() } returns gwei(100)
        stubFeeHistory(listOf(gwei(5)))

        val fee = service.calculateFees(swap(Chain.Ethereum)) as Eip1559

        assertEquals(DEFAULT_SWAP_LIMIT, fee.limit)
        assertEquals(gwei(110), fee.networkPrice)
    }

    @Test
    fun `calculateFees uses Mantle-specific limit for Mantle swap transactions`() = runTest {
        coEvery { evmApi.getBaseFee() } returns gwei(100)
        stubFeeHistory(listOf(gwei(5)))

        val fee = service.calculateFees(swap(Chain.Mantle)) as Eip1559

        assertEquals(DEFAULT_MANTLE_SWAP_LIMIT, fee.limit)
        assertEquals(gwei(110), fee.networkPrice)
    }

    // ---------- Helpers ----------

    private fun transfer(chain: Chain, isNative: Boolean = true) =
        Transfer(
            coin = coin(chain, isNative),
            vault = VAULT,
            amount = BigInteger("1000000000000000000"),
            to = "0xRecipient",
        )

    private fun swap(chain: Chain) =
        Swap(
            coin = coin(chain, isNative = true),
            vault = VAULT,
            amount = BigInteger("1000000000000000000"),
            to = "0xRecipient",
            callData = "",
            approvalData = null,
        )

    private fun coin(chain: Chain, isNative: Boolean) =
        Coin(
            chain = chain,
            ticker = "TEST",
            logo = "",
            address = "0xSender",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = if (isNative) "" else "0xContract",
            isNativeToken = isNative,
        )

    private fun stubFeeHistory(rewards: List<BigInteger>) {
        coEvery { evmApi.getFeeHistory() } returns rewards
    }

    private fun gwei(n: Long): BigInteger = GWEI.multiply(BigInteger.valueOf(n))

    companion object {
        private val VAULT = VaultData(vaultHexPublicKey = "pub", vaultHexChainCode = "chain")
        private val GWEI = BigInteger.TEN.pow(9)
        private val HUNDRED = BigInteger("100")
        private val POLYGON_MIN_TIP = GWEI.multiply(BigInteger("30"))
        private val L2_DEFAULT_TIP = BigInteger("20")
        private val BLAST_MIN_TIP = BigInteger.TEN.pow(7) // 0.01 GWEI
    }
}
