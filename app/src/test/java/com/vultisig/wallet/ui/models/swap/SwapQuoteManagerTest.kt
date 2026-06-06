@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import com.vultisig.wallet.data.usecases.ConvertTokenToToken
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.SearchTokenUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SwapQuoteManagerTest {

    private val swapQuoteRepository: SwapQuoteRepository = mockk(relaxed = true)
    private val tokenRepository: TokenRepository = mockk(relaxed = true)
    private val tokenPriceRepository: TokenPriceRepository = mockk(relaxed = true)
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase = mockk(relaxed = true)
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper =
        mockk(relaxed = true)
    private val fiatValueToString: FiatValueToStringMapper = mockk(relaxed = true)
    private val searchToken: SearchTokenUseCase = mockk(relaxed = true)
    private val convertTokenToTokenUseCase: ConvertTokenToToken = mockk(relaxed = true)

    private fun createManager() =
        SwapQuoteManager(
            swapQuoteRepository = swapQuoteRepository,
            tokenRepository = tokenRepository,
            tokenPriceRepository = tokenPriceRepository,
            convertTokenValueToFiat = convertTokenValueToFiat,
            mapTokenValueToDecimalUiString = mapTokenValueToDecimalUiString,
            fiatValueToString = fiatValueToString,
            searchToken = searchToken,
            convertTokenToTokenUseCase = convertTokenToTokenUseCase,
        )

    @Test
    fun `fetchBestQuote converts all-timeout failures to SwapException_TimeOut`() = runTest {
        coEvery { tokenRepository.getNativeToken(any()) } coAnswers
            {
                delay(Long.MAX_VALUE)
                error("unreachable")
            }

        val manager = createManager()
        val deferred = async {
            runCatching {
                manager.fetchBestQuote(
                    candidates =
                        listOf(
                            QuoteCandidate(
                                provider = SwapProvider.THORCHAIN,
                                vultBPSDiscount = null,
                                referral = null,
                            )
                        ),
                    src = mockk(relaxed = true),
                    dst = mockk(relaxed = true),
                    srcToken = mockk(relaxed = true),
                    dstToken = mockk(relaxed = true),
                    srcTokenValue = BigInteger.ONE,
                    tokenValue = mockk(relaxed = true),
                    currency = AppCurrency.USD,
                    amount = BigDecimal.ONE,
                )
            }
        }

        advanceTimeBy(15_001L)

        assertIs<SwapException.TimeOut>(deferred.await().exceptionOrNull())
    }

    @Test
    fun `fetchBestQuote surfaces dust error over generic no-route error`() = runTest {
        // convertTokenValueToFiat is a relaxed suspend function-type mock; relaxed answers for
        // such interfaces return a bare Object that can't be cast to FiatValue, throwing a
        // ClassCastException before getQuote runs. Stub it so both providers reach getQuote and
        // surface their typed dust/no-route errors.
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)
        coEvery { swapQuoteRepository.getQuote(SwapProvider.MAYA, any()) } throws
            SwapException.AmountBelowDustThreshold("amount below dust threshold")
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } throws
            SwapException.SwapRouteNotAvailable("no route available")

        val manager = createManager()
        // THORCHAIN is listed first so that reverting to first-by-provider-order would surface
        // the generic SwapRouteNotAvailable instead of MAYA's actionable dust error.
        val result = runCatching {
            manager.fetchBestQuote(
                candidates =
                    listOf(
                        QuoteCandidate(
                            provider = SwapProvider.THORCHAIN,
                            vultBPSDiscount = null,
                            referral = null,
                        ),
                        QuoteCandidate(
                            provider = SwapProvider.MAYA,
                            vultBPSDiscount = null,
                            referral = null,
                        ),
                    ),
                src = mockk(relaxed = true),
                dst = mockk(relaxed = true),
                srcToken = mockk(relaxed = true),
                dstToken = mockk(relaxed = true),
                srcTokenValue = BigInteger.ONE,
                tokenValue = mockk(relaxed = true),
                currency = AppCurrency.USD,
                amount = BigDecimal.ONE,
            )
        }

        result.exceptionOrNull().shouldBeInstanceOf<SwapException.AmountBelowDustThreshold>()
    }

    @Test
    fun `fetchQuote keeps the SwapKit sub-provider label across a cache hit (Native path)`() =
        runTest {
            // Pins the SwapQuoteResult.Native arm + the `is SwapQuote.SwapKit -> subProvider`
            // label:
            // a non-EVM SwapKit quote must surface "SwapKit (NEAR)", and that must survive a cache
            // hit (second fetch skips the lambda) rather than collapse to the generic "SwapKit".
            val btc = coin(Chain.Bitcoin, "BTC", address = "bc1qsrc", decimals = 8)
            val eth = coin(Chain.Ethereum, "ETH", address = "0xdst", decimals = 18)
            val swapKitQuote =
                SwapQuote.SwapKit(
                    expectedDstValue = TokenValue(BigInteger.ONE, eth),
                    fees = TokenValue(BigInteger.valueOf(400), btc),
                    expiredAt = Clock.System.now().plus(5.minutes),
                    data = mockk(relaxed = true),
                    subProvider = "NEAR",
                )
            coEvery { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, any()) } returns
                SwapQuoteResult.Native(swapKitQuote)
            // Relaxed mockk returns Object for these fun-type mappers; give them real return types
            // so the SwapKit (null-rawFees) fiat-formatting path doesn't ClassCastException.
            coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
                FiatValue(BigDecimal.ZERO, "USD")
            every { mapTokenValueToDecimalUiString(any()) } returns "0"

            val manager = createManager()
            val first =
                manager.fetchQuote(
                    SwapProvider.SWAPKIT,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    btc,
                    eth,
                    BigInteger.ONE,
                    TokenValue(BigInteger.ONE, btc),
                    AppCurrency.USD,
                    null,
                    null,
                    BigDecimal.ONE,
                )
            val second =
                manager.fetchQuote(
                    SwapProvider.SWAPKIT,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    btc,
                    eth,
                    BigInteger.ONE,
                    TokenValue(BigInteger.ONE, btc),
                    AppCurrency.USD,
                    null,
                    null,
                    BigDecimal.ONE,
                )

            assertEquals(UiText.DynamicString("SwapKit (NEAR)"), first.providerUiText)
            assertEquals(UiText.DynamicString("SwapKit (NEAR)"), second.providerUiText)
            // Second call is a cache hit (same pair/amount/addresses) — the lambda is skipped.
            coVerify(exactly = 1) { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, any()) }
        }

    @Test
    fun `fetchBestQuote picks the highest estimatedDstFiat, not the candidate order`() = runTest {
        // The provider table hands candidates back in a fixed set order (THORCHAIN before SWAPKIT
        // on Bitcoin), but selection is `successes.maxBy { estimatedDstFiat }` — the route the user
        // receives the most on. Pin that: SWAPKIT is listed second yet prices the dst higher, so it
        // must win. This is what makes SwapKit reachable on any BTC pair it out-prices, regardless
        // of where it sits in the eligibility set.
        val btc = coin(Chain.Bitcoin, "BTC", address = "bc1qsrc", decimals = 8)
        val eth = coin(Chain.Ethereum, "ETH", address = "0xdst", decimals = 18)

        val thorDst = TokenValue(BigInteger.valueOf(100), eth)
        val swapKitDst = TokenValue(BigInteger.valueOf(200), eth)

        val thorQuote =
            SwapQuote.ThorChain(
                expectedDstValue = thorDst,
                fees = TokenValue(BigInteger.ZERO, eth),
                expiredAt = Clock.System.now().plus(5.minutes),
                recommendedMinTokenValue = TokenValue(BigInteger.ZERO, eth),
                data =
                    THORChainSwapQuote(
                        dustThreshold = null,
                        expectedAmountOut = "100",
                        expiry = BigInteger.ZERO,
                        fees = Fees(affiliate = "0", asset = "0", outbound = "0", total = "0"),
                        inboundAddress = "thorinbound",
                        inboundConfirmationBlocks = null,
                        inboundConfirmationSeconds = null,
                        maxStreamingQuantity = 0,
                        memo = "memo",
                        notes = "",
                        outboundDelayBlocks = BigInteger.ZERO,
                        outboundDelaySeconds = BigInteger.ZERO,
                        recommendedMinAmountIn = "0",
                        streamingSwapBlocks = BigInteger.ZERO,
                        totalSwapSeconds = 0L,
                        warning = "",
                        router = null,
                        error = null,
                    ),
            )
        val swapKitQuote =
            SwapQuote.SwapKit(
                expectedDstValue = swapKitDst,
                fees = TokenValue(BigInteger.valueOf(400), btc),
                expiredAt = Clock.System.now().plus(5.minutes),
                data = mockk(relaxed = true),
                subProvider = null,
            )

        coEvery { tokenRepository.getNativeToken(any()) } returns btc
        // Catch-all first so the dst-specific ranking stubs (defined after) take precedence.
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        coEvery { convertTokenValueToFiat(any(), thorDst, any()) } returns
            FiatValue(BigDecimal("100"), "USD")
        coEvery { convertTokenValueToFiat(any(), swapKitDst, any()) } returns
            FiatValue(BigDecimal("200"), "USD")
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } returns
            SwapQuoteResult.Native(thorQuote)
        coEvery { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, any()) } returns
            SwapQuoteResult.Native(swapKitQuote)

        val best =
            createManager()
                .fetchBestQuote(
                    candidates =
                        listOf(
                            QuoteCandidate(SwapProvider.THORCHAIN, null, null),
                            QuoteCandidate(SwapProvider.SWAPKIT, null, null),
                        ),
                    src = mockk(relaxed = true),
                    dst = mockk(relaxed = true),
                    srcToken = btc,
                    dstToken = eth,
                    srcTokenValue = BigInteger.ONE,
                    tokenValue = TokenValue(BigInteger.ONE, btc),
                    currency = AppCurrency.USD,
                    amount = BigDecimal.ONE,
                )

        assertEquals(SwapProvider.SWAPKIT, best.candidate.provider)
        assertEquals(BigDecimal("200"), best.result.estimatedDstFiat.value)
    }

    @Test
    fun `fetchBestQuote prefers higher-priority provider on a near-tie within the 1pct band`() =
        runTest {
            // THORChain (priority 0) prices the dst slightly lower than SwapKit (priority 2), but
            // within the 1% band (99.5 vs 100, floor = 99). Both are in-band, so the banded layer
            // tilts to the higher-priority THORChain instead of the marginally larger raw output.
            val best = rankTwoProviders(thorFiat = "99.5", swapKitFiat = "100")

            assertEquals(SwapProvider.THORCHAIN, best.candidate.provider)
            assertEquals(BigDecimal("99.5"), best.result.estimatedDstFiat.value)
        }

    @Test
    fun `fetchBestQuote keeps the materially-better quote when it is outside the 1pct band`() =
        runTest {
            // THORChain prices 2% below SwapKit (98 vs 100, floor = 99). THORChain falls outside
            // the
            // band, so the priority preference does not apply and the better-rate SwapKit wins.
            val best = rankTwoProviders(thorFiat = "98", swapKitFiat = "100")

            assertEquals(SwapProvider.SWAPKIT, best.candidate.provider)
            assertEquals(BigDecimal("100"), best.result.estimatedDstFiat.value)
        }

    /**
     * Runs [SwapQuoteManager.fetchBestQuote] over a THORChain + SwapKit candidate pair on a
     * BTC->ETH pair, where each provider's dst is converted to [thorFiat] / [swapKitFiat]
     * respectively. The raw dst token amounts are arbitrary; ranking is driven purely by the mocked
     * fiat values.
     */
    private suspend fun rankTwoProviders(thorFiat: String, swapKitFiat: String): BestQuote {
        val btc = coin(Chain.Bitcoin, "BTC", address = "bc1qsrc", decimals = 8)
        val eth = coin(Chain.Ethereum, "ETH", address = "0xdst", decimals = 18)

        val thorDst = TokenValue(BigInteger.valueOf(100), eth)
        val swapKitDst = TokenValue(BigInteger.valueOf(200), eth)

        val thorQuote =
            SwapQuote.ThorChain(
                expectedDstValue = thorDst,
                fees = TokenValue(BigInteger.ZERO, eth),
                expiredAt = Clock.System.now().plus(5.minutes),
                recommendedMinTokenValue = TokenValue(BigInteger.ZERO, eth),
                data =
                    THORChainSwapQuote(
                        dustThreshold = null,
                        expectedAmountOut = "100",
                        expiry = BigInteger.ZERO,
                        fees = Fees(affiliate = "0", asset = "0", outbound = "0", total = "0"),
                        inboundAddress = "thorinbound",
                        inboundConfirmationBlocks = null,
                        inboundConfirmationSeconds = null,
                        maxStreamingQuantity = 0,
                        memo = "memo",
                        notes = "",
                        outboundDelayBlocks = BigInteger.ZERO,
                        outboundDelaySeconds = BigInteger.ZERO,
                        recommendedMinAmountIn = "0",
                        streamingSwapBlocks = BigInteger.ZERO,
                        totalSwapSeconds = 0L,
                        warning = "",
                        router = null,
                        error = null,
                    ),
            )
        val swapKitQuote =
            SwapQuote.SwapKit(
                expectedDstValue = swapKitDst,
                fees = TokenValue(BigInteger.valueOf(400), btc),
                expiredAt = Clock.System.now().plus(5.minutes),
                data = mockk(relaxed = true),
                subProvider = null,
            )

        coEvery { tokenRepository.getNativeToken(any()) } returns btc
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        coEvery { convertTokenValueToFiat(any(), thorDst, any()) } returns
            FiatValue(BigDecimal(thorFiat), "USD")
        coEvery { convertTokenValueToFiat(any(), swapKitDst, any()) } returns
            FiatValue(BigDecimal(swapKitFiat), "USD")
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } returns
            SwapQuoteResult.Native(thorQuote)
        coEvery { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, any()) } returns
            SwapQuoteResult.Native(swapKitQuote)

        return createManager()
            .fetchBestQuote(
                candidates =
                    listOf(
                        QuoteCandidate(SwapProvider.THORCHAIN, null, null),
                        QuoteCandidate(SwapProvider.SWAPKIT, null, null),
                    ),
                src = mockk(relaxed = true),
                dst = mockk(relaxed = true),
                srcToken = btc,
                dstToken = eth,
                srcTokenValue = BigInteger.ONE,
                tokenValue = TokenValue(BigInteger.ONE, btc),
                currency = AppCurrency.USD,
                amount = BigDecimal.ONE,
            )
    }

    @Test
    fun `computeIndicativeQuote derives dst from cached spot prices`() = runTest {
        val src = coin(Chain.Ethereum, "ETH", "0xsrc", 18)
        val dst = coin(Chain.Bitcoin, "BTC", "btc", 8)
        coEvery { tokenPriceRepository.getCachedPrice(src.id, AppCurrency.USD) } returns
            BigDecimal("2000")
        coEvery { tokenPriceRepository.getCachedPrice(dst.id, AppCurrency.USD) } returns
            BigDecimal("40000")

        val dstTokenValueSlot = slot<TokenValue>()
        every { mapTokenValueToDecimalUiString(capture(dstTokenValueSlot)) } returns "0.1"
        coEvery { fiatValueToString(any(), any()) } returns "$4,000.00"

        val result =
            createManager().computeIndicativeQuote(src, dst, BigDecimal("2"), AppCurrency.USD)

        assertNotNull(result)
        assertEquals("0.1", result.estimatedDstTokenValue)
        assertEquals("$4,000.00", result.estimatedDstFiatValue)
        // 2 ETH * $2000 / $40000 = 0.1 BTC
        assertEquals(BigDecimal("0.1"), dstTokenValueSlot.captured.decimal.stripTrailingZeros())
    }

    @Test
    fun `computeIndicativeQuote returns null when a price is not cached`() = runTest {
        val src = coin(Chain.Ethereum, "ETH", "0xsrc", 18)
        val dst = coin(Chain.Bitcoin, "BTC", "btc", 8)
        coEvery { tokenPriceRepository.getCachedPrice(src.id, AppCurrency.USD) } returns
            BigDecimal("2000")
        coEvery { tokenPriceRepository.getCachedPrice(dst.id, AppCurrency.USD) } returns null

        val result =
            createManager().computeIndicativeQuote(src, dst, BigDecimal("2"), AppCurrency.USD)

        assertEquals(null, result)
    }

    @Test
    fun `computeIndicativeQuote returns null for zero price, zero amount, or same token`() =
        runTest {
            val src = coin(Chain.Ethereum, "ETH", "0xsrc", 18)
            val dst = coin(Chain.Bitcoin, "BTC", "btc", 8)
            coEvery { tokenPriceRepository.getCachedPrice(any(), AppCurrency.USD) } returns
                BigDecimal.ZERO
            val manager = createManager()

            assertEquals(
                null,
                manager.computeIndicativeQuote(src, dst, BigDecimal("2"), AppCurrency.USD),
            )
            assertEquals(
                null,
                manager.computeIndicativeQuote(src, dst, BigDecimal.ZERO, AppCurrency.USD),
            )
            assertEquals(
                null,
                manager.computeIndicativeQuote(src, src, BigDecimal("2"), AppCurrency.USD),
            )
        }

    private fun coin(chain: Chain, ticker: String, address: String, decimals: Int) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = address,
            decimal = decimals,
            hexPublicKey = "pub",
            priceProviderID = ticker.lowercase(),
            contractAddress = "",
            isNativeToken = true,
        )
}
