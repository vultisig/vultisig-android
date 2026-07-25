package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.ThorMimirRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BuildLimitSwapTransactionUseCaseTest {

    private val thorChainApi = mockk<ThorChainApi>()
    private val thorMimirRepository = mockk<ThorMimirRepository>()
    private val swapGasCalculator = mockk<SwapGasCalculator>()
    private val allowanceRepository = mockk<AllowanceRepository>()

    private val useCase =
        BuildLimitSwapTransactionUseCase(
            thorChainApi = thorChainApi,
            thorMimirRepository = thorMimirRepository,
            swapGasCalculator = swapGasCalculator,
            allowanceRepository = allowanceRepository,
        )

    private val btcInbound = "bc1qasgardinboundvaultxxxxxxxxxxxxxxxxxx0wlh"
    private val ethAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f12345"
    private val btc =
        coin(Chain.Bitcoin, "BTC", address = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", 8, true)
    private val eth = coin(Chain.Ethereum, "ETH", address = ethAddress, 18, true)

    private fun params() =
        BuildLimitSwapTransactionUseCase.Params(
            vaultId = "vault",
            srcToken = btc,
            dstToken = eth,
            srcAddress = btc.address,
            srcTokenValue = TokenValue(BigInteger("100000000"), token = btc),
            targetPrice = BigDecimal("16"),
            expiryHours = 24,
            destinationAddress = eth.address,
            gasFee = TokenValue(BigInteger("1000"), token = btc),
            gasFeeFiatValue = FiatValue(BigDecimal.ZERO, "USD"),
            estimatedNetworkFeeTokenValue = null,
            estimatedNetworkFeeFiatValue = null,
            now = 0L,
        )

    @Test
    fun `fails closed when the advanced swap queue is disabled`() = runTest {
        coEvery { thorMimirRepository.isAdvancedSwapQueueEnabled() } returns false
        val error = runCatching { useCase.build(params()) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("advanced swap queue is disabled"))
    }

    @Test
    fun `native gas-asset source deposits to the inbound vault with a limit memo`() = runTest {
        coEvery { thorMimirRepository.isAdvancedSwapQueueEnabled() } returns true
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(inbound("BTC", btcInbound, router = null))
        coEvery {
            swapGasCalculator.getSpecificAndUtxo(any(), any(), any(), any(), any(), any(), any())
        } returns mockk(relaxed = true)
        coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns null

        val tx = useCase.build(params())

        // 1 BTC * 16 ETH/BTC -> LIM 1_600_000_000 (1e8), interval 24h = 14400 blocks.
        assertEquals("=<:ETH.ETH:$ethAddress:1600000000/14400/0:va:50", tx.memo)
        // Native gas source: funds go to the Asgard inbound vault, no router, no approval.
        assertEquals(btcInbound, tx.dstAddress)
        assertFalse(tx.isApprovalRequired)
        val payload = tx.payload as SwapPayload.ThorChain
        assertEquals(btcInbound, payload.data.vaultAddress)
        assertEquals(null, payload.data.routerAddress)
    }

    @Test
    fun `RUNE source deposits to the signer's own address (MsgDeposit)`() = runTest {
        val rune =
            coin(
                Chain.ThorChain,
                "RUNE",
                address = "thor1x2whgc2nt665y0kc44uywhynazvp0l8tp0vtu6",
                8,
                true,
            )
        coEvery { thorMimirRepository.isAdvancedSwapQueueEnabled() } returns true
        coEvery {
            swapGasCalculator.getSpecificAndUtxo(any(), any(), any(), any(), any(), any(), any())
        } returns mockk(relaxed = true)
        coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns null

        val tx =
            useCase.build(
                params()
                    .copy(
                        srcToken = rune,
                        srcAddress = rune.address,
                        srcTokenValue = TokenValue(BigInteger("100000000"), token = rune),
                    )
            )

        assertEquals(rune.address, tx.dstAddress)
        assertTrue(tx.memo!!.startsWith("=<:ETH.ETH:$ethAddress:"))
    }

    private fun inbound(chain: String, address: String, router: String?) =
        THORChainInboundAddress(chain = chain, address = address, halted = false, router = router)

    private fun coin(
        chain: Chain,
        ticker: String,
        address: String,
        decimals: Int,
        native: Boolean,
    ) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = address,
            decimal = decimals,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = native,
        )
}
