package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.ThorMimirRepository
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
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
    private val swapQuoteRepository = mockk<SwapQuoteRepository>()

    private val useCase =
        BuildLimitSwapTransactionUseCase(
            thorChainApi = thorChainApi,
            thorMimirRepository = thorMimirRepository,
            swapGasCalculator = swapGasCalculator,
            allowanceRepository = allowanceRepository,
            swapQuoteRepository = swapQuoteRepository,
        )

    private val btcInbound = "bc1qasgardinboundvaultxxxxxxxxxxxxxxxxxx0wlh"
    private val thorRouter = "0xD37BbE5744D730a1d98d8DC97c42F0Ca46aD7146"
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
        assertTrue(error?.message.orEmpty().contains("advanced swap queue is disabled"))
    }

    @Test
    fun `native gas-asset source deposits to the inbound vault with a limit memo`() = runTest {
        coEvery { thorMimirRepository.isAdvancedSwapQueueEnabled() } returns true
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(inbound("BTC", btcInbound))
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } returns quote()
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
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } returns quote()
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

    @Test
    fun `ERC20 source deposits through the router the swap quote reports`() = runTest {
        coEvery { thorMimirRepository.isAdvancedSwapQueueEnabled() } returns true
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(inbound("ETH", "0xInboundVault"))
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } returns
            quote(router = thorRouter)
        coEvery {
            swapGasCalculator.getSpecificAndUtxo(any(), any(), any(), any(), any(), any(), any())
        } returns mockk(relaxed = true)
        coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns null

        val tx = useCase.build(usdcParams())

        // The ERC20 deposit is made to the router (depositWithExpiry carries the memo), not to the
        // inbound vault — and the router comes off the ordinary swap quote.
        assertEquals(thorRouter, tx.dstAddress)
        val payload = tx.payload as SwapPayload.ThorChain
        assertEquals(thorRouter, payload.data.routerAddress)
    }

    @Test
    fun `fails closed when an ERC20 source has no THORChain router`() = runTest {
        coEvery { thorMimirRepository.isAdvancedSwapQueueEnabled() } returns true
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(inbound("ETH", "0xInboundVault"))
        // Quote carries no router — a plain transfer would drop the memo and strand the tokens
        // instead of placing a protected order, so build() must reject the route.
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } returns
            quote(router = null)
        coEvery {
            swapGasCalculator.getSpecificAndUtxo(any(), any(), any(), any(), any(), any(), any())
        } returns mockk(relaxed = true)
        coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns null

        val error = runCatching { useCase.build(usdcParams()) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("router"))
    }

    @Test
    fun `fails closed when the router quote itself fails`() = runTest {
        coEvery { thorMimirRepository.isAdvancedSwapQueueEnabled() } returns true
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(inbound("ETH", "0xInboundVault"))
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } throws
            IllegalStateException("thornode unavailable")
        coEvery {
            swapGasCalculator.getSpecificAndUtxo(any(), any(), any(), any(), any(), any(), any())
        } returns mockk(relaxed = true)
        coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns null

        val error = runCatching { useCase.build(usdcParams()) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("router"))
    }

    @Test
    fun `carries the quote's affiliate and outbound fees, denominated in the bought asset`() =
        runTest {
            coEvery { thorMimirRepository.isAdvancedSwapQueueEnabled() } returns true
            coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
                listOf(inbound("BTC", btcInbound))
            // THORChain reports fees in its 1e8 fixed point: 0.005 / 0.02 / 0.025 ETH.
            coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } returns
                quote(affiliate = "500000", outbound = "2000000", total = "2500000")
            coEvery {
                swapGasCalculator.getSpecificAndUtxo(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns mockk(relaxed = true)
            coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns null

            val tx = useCase.build(params())

            // Rescaled from 1e8 into ETH's 18 decimals, and denominated in the bought asset — the
            // gas fee used to land here and be read as an ETH amount by the verify screen.
            assertEquals(eth.ticker, tx.swapFee?.unit)
            assertEquals(BigInteger("5000000000000000"), tx.swapFee?.value)
            assertEquals(BigInteger("20000000000000000"), tx.outboundFee?.value)
            assertEquals(BigInteger("25000000000000000"), tx.estimatedFees.value)
            // The network fee stays in the sold asset and is unaffected by the breakdown.
            assertEquals(btc.ticker, tx.gasFees.unit)
        }

    @Test
    fun `a failed quote drops the fee breakdown instead of blocking a native placement`() =
        runTest {
            coEvery { thorMimirRepository.isAdvancedSwapQueueEnabled() } returns true
            coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
                listOf(inbound("BTC", btcInbound))
            coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } throws
                IllegalStateException("thornode unavailable")
            coEvery {
                swapGasCalculator.getSpecificAndUtxo(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns mockk(relaxed = true)
            coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns null

            val tx = useCase.build(params())

            // The memo is fully determined without the quote, so the order is still placeable —
            // it just shows no fee breakdown, and never the gas fee mispriced as the bought asset.
            assertEquals("=<:ETH.ETH:$ethAddress:1600000000/14400/0:va:50", tx.memo)
            assertEquals(null, tx.swapFee)
            assertEquals(null, tx.outboundFee)
            assertEquals(BigInteger.ZERO, tx.estimatedFees.value)
            assertEquals(eth.ticker, tx.estimatedFees.unit)
        }

    @Test
    fun `rejects a trading-paused inbound at placement`() = runTest {
        coEvery { thorMimirRepository.isAdvancedSwapQueueEnabled() } returns true
        coEvery { thorChainApi.getTHORChainInboundAddresses() } returns
            listOf(inbound("BTC", btcInbound, chainTradingPaused = true))
        coEvery {
            swapGasCalculator.getSpecificAndUtxo(any(), any(), any(), any(), any(), any(), any())
        } returns mockk(relaxed = true)
        coEvery { allowanceRepository.getAllowance(any(), any(), any(), any()) } returns null

        val error = runCatching { useCase.build(params()) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("No live THORChain inbound"))
    }

    private val usdc =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = ethAddress,
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            isNativeToken = false,
        )

    private fun usdcParams() =
        params()
            .copy(
                srcToken = usdc,
                srcAddress = usdc.address,
                srcTokenValue = TokenValue(BigInteger("1000000"), token = usdc),
            )

    /** A THORChain quote carrying only the fields the placement reads: router and fee breakdown. */
    private fun quote(
        router: String? = null,
        affiliate: String = "0",
        outbound: String = "0",
        total: String = "0",
    ) =
        SwapQuoteResult.Native(
            SwapQuote.ThorChain(
                expectedDstValue = TokenValue(BigInteger.ZERO, eth),
                fees = TokenValue(BigInteger.ZERO, eth),
                recommendedMinTokenValue = TokenValue(BigInteger.ZERO, eth),
                expiredAt = Instant.MAX,
                data =
                    mockk<THORChainSwapQuote>(relaxed = true) {
                        every { this@mockk.router } returns router
                        every { fees } returns
                            Fees(
                                affiliate = affiliate,
                                asset = "0",
                                outbound = outbound,
                                total = total,
                            )
                    },
            )
        )

    private fun inbound(
        chain: String,
        address: String,
        halted: Boolean = false,
        globalTradingPaused: Boolean = false,
        chainTradingPaused: Boolean = false,
    ) =
        THORChainInboundAddress(
            chain = chain,
            address = address,
            halted = halted,
            globalTradingPaused = globalTradingPaused,
            chainTradingPaused = chainTradingPaused,
        )

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
