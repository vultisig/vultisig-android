@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.models.ZkGasFee
import com.vultisig.wallet.data.blockchain.model.Eip1559
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ZkFeeServiceTest {

    private val evmApi: EvmApi = mockk()
    private val evmApiFactory: EvmApiFactory = mockk()
    private val service = ZkFeeService(evmApiFactory)

    @BeforeEach
    fun setUp() {
        every { evmApiFactory.createEvmApi(any()) } returns evmApi
        coEvery { evmApi.zkEstimateFee(any(), any(), any()) } returns
            ZkGasFee(
                gasLimit = BigInteger("21000"),
                gasPerPubdataLimit = BigInteger.ONE,
                maxFeePerGas = BigInteger("7"),
                maxPriorityFeePerGas = BigInteger("2"),
            )
    }

    @Test
    fun `calculateFees accepts swap transactions`() = runTest {
        val fee = service.calculateFees(swap()) as Eip1559

        assertEquals(BigInteger("21000"), fee.limit)
        assertEquals(BigInteger("7"), fee.maxFeePerGas)
        assertEquals(BigInteger("2"), fee.maxPriorityFeePerGas)
        coVerify(exactly = 1) { evmApi.zkEstimateFee("0xSender", "0xRecipient", "0xdeadbeef") }
    }

    @Test
    fun `a swap prices the router calldata it will broadcast`() = runTest {
        service.calculateFees(swap(callData = "a9059cbb00"))

        coVerify(exactly = 1) { evmApi.zkEstimateFee("0xSender", "0xRecipient", "0xa9059cbb00") }
    }

    @Test
    fun `a swap with no known router calldata prices an empty payload`() = runTest {
        // Router calldata is fetched in parallel with the quote, so callers that build a Swap
        // purely to price it leave it empty — there is nothing to build in that case.
        service.calculateFees(swap(callData = ""))

        coVerify(exactly = 1) { evmApi.zkEstimateFee("0xSender", "0xRecipient", "0x") }
    }

    @Test
    fun `priority fee is clamped to max fee when the RPC returns a tip above the cap`() = runTest {
        coEvery { evmApi.zkEstimateFee(any(), any(), any()) } returns
            ZkGasFee(
                gasLimit = BigInteger("21000"),
                gasPerPubdataLimit = BigInteger.ONE,
                maxFeePerGas = BigInteger("7"),
                maxPriorityFeePerGas = BigInteger("9"),
            )

        val fee = service.calculateFees(transfer()) as Eip1559

        assertEquals(BigInteger("7"), fee.maxFeePerGas)
        assertEquals(BigInteger("7"), fee.maxPriorityFeePerGas)
    }

    @Test
    fun `a negative priority fee is floored at zero rather than passed through`() = runTest {
        // Ordering alone would let this through: -9 is already below the cap. Left signed, it
        // reaches WalletCore as two's-complement bytes read back unsigned.
        coEvery { evmApi.zkEstimateFee(any(), any(), any()) } returns
            ZkGasFee(
                gasLimit = BigInteger("21000"),
                gasPerPubdataLimit = BigInteger.ONE,
                maxFeePerGas = BigInteger("7"),
                maxPriorityFeePerGas = BigInteger("-9"),
            )

        val fee = service.calculateFees(transfer()) as Eip1559

        assertEquals(BigInteger.ZERO, fee.maxPriorityFeePerGas)
    }

    @Test
    fun `a negative max fee cannot drag the priority fee below zero`() = runTest {
        coEvery { evmApi.zkEstimateFee(any(), any(), any()) } returns
            ZkGasFee(
                gasLimit = BigInteger("21000"),
                gasPerPubdataLimit = BigInteger.ONE,
                maxFeePerGas = BigInteger("-7"),
                maxPriorityFeePerGas = BigInteger("2"),
            )

        val fee = service.calculateFees(transfer()) as Eip1559

        assertEquals(BigInteger.ZERO, fee.maxPriorityFeePerGas)
    }

    @Test
    fun `priority fee below the max fee is left untouched`() = runTest {
        val fee = service.calculateFees(transfer()) as Eip1559

        assertEquals(BigInteger("7"), fee.maxFeePerGas)
        assertEquals(BigInteger("2"), fee.maxPriorityFeePerGas)
    }

    @Test
    fun `calculateDefaultFees uses the same zk estimate path`() = runTest {
        val fee = service.calculateDefaultFees(transfer()) as Eip1559

        assertEquals(BigInteger("21000"), fee.limit)
        assertEquals(BigInteger("147000"), fee.amount)
        coVerify(exactly = 1) { evmApi.zkEstimateFee("0xSender", "0xRecipient", "0x") }
    }

    @Test
    fun `a native transfer without a memo prices an empty payload`() = runTest {
        service.calculateFees(transfer())

        coVerify(exactly = 1) { evmApi.zkEstimateFee("0xSender", "0xRecipient", "0x") }
    }

    @Test
    fun `a native transfer prices the memo it will broadcast`() = runTest {
        service.calculateFees(transfer(memo = "hi"))

        // "hi" UTF-8 encoded, exactly what the signing path puts in the transaction payload.
        coVerify(exactly = 1) { evmApi.zkEstimateFee("0xSender", "0xRecipient", "0x6869") }
    }

    @Test
    fun `a longer memo is priced as a longer payload`() = runTest {
        // zkSync charges gas and pubdata per calldata byte, so the payload the estimate carries has
        // to grow with the memo instead of staying a fixed-size stand-in.
        service.calculateFees(transfer(memo = "hi there"))

        coVerify(exactly = 1) {
            evmApi.zkEstimateFee("0xSender", "0xRecipient", "0x6869207468657265")
        }
    }

    @Test
    fun `a hex memo is priced as its decoded bytes, matching the signing path`() = runTest {
        // toByteStringOrHex decodes a hex-looking memo instead of UTF-8 encoding it, so these four
        // bytes must not be priced as the ten characters they are written with.
        service.calculateFees(transfer(memo = "0xdeadbeef"))

        coVerify(exactly = 1) { evmApi.zkEstimateFee("0xSender", "0xRecipient", "0xdeadbeef") }
    }

    @Test
    fun `an ERC-20 transfer prices the transfer call to the token contract`() = runTest {
        service.calculateFees(
            transfer(
                coin = coin(isNativeToken = false, contractAddress = "0xToken"),
                to = RECIPIENT_ADDRESS,
            )
        )

        // transfer(address,uint256) to the token contract — an ERC-20 send never calls the
        // recipient directly, so estimating against it priced a bare value transfer.
        coVerify(exactly = 1) {
            evmApi.zkEstimateFee(
                "0xSender",
                "0xToken",
                "0xa9059cbb" +
                    "000000000000000000000000${RECIPIENT_ADDRESS.removePrefix("0x")}" +
                    "0000000000000000000000000000000000000000000000000000000000000001",
            )
        }
    }

    private fun transfer(memo: String? = null, coin: Coin = coin(), to: String = "0xRecipient") =
        Transfer(coin = coin, vault = VAULT, amount = BigInteger.ONE, to = to, memo = memo)

    private fun swap(callData: String = "0xdeadbeef") =
        Swap(
            coin = coin(),
            vault = VAULT,
            amount = BigInteger.ONE,
            to = "0xRecipient",
            callData = callData,
            approvalData = null,
        )

    private fun coin(isNativeToken: Boolean = true, contractAddress: String = "") =
        Coin(
            chain = Chain.ZkSync,
            ticker = "ETH",
            logo = "",
            address = "0xSender",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = isNativeToken,
        )

    private companion object {
        private val VAULT = VaultData(vaultHexPublicKey = "pub", vaultHexChainCode = "chain")
        private const val RECIPIENT_ADDRESS = "0x00000000000000000000000000000000000000aa"
    }
}
