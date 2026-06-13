@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.R
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
import io.kotest.matchers.shouldBe
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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
    fun `fetchQuote clamps an inflated destination fiat to the source fiat`() = runTest {
        // ETH -> BINU on Base (#4878): the destination's CoinGecko mark ($13.18) is ~2.5x the rate
        // the quote actually executes at, while the source ETH is valued accurately ($5.26). A swap
        // is value-preserving, so the displayed destination fiat must clamp down to the source fiat
        // rather than imply a free lunch. The unclamped market value still drives cross-provider
        // ranking.
        val eth = coin(Chain.Ethereum, "ETH", "0xsrc", 18)
        val binu = coin(Chain.Base, "BINU", "0xdst", 18)
        val srcTokenValue = TokenValue(BigInteger.valueOf(3_174_080_000_000_000L), eth)
        val dstValue = TokenValue(BigInteger.valueOf(35_900_000L), binu)
        val quote =
            SwapQuote.SwapKit(
                expectedDstValue = dstValue,
                fees = TokenValue(BigInteger.ZERO, eth),
                expiredAt = Clock.System.now().plus(5.minutes),
                data = mockk(relaxed = true),
                subProvider = null,
            )

        coEvery { tokenRepository.getNativeToken(any()) } returns eth
        coEvery { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, any()) } returns
            SwapQuoteResult.Native(quote)
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        coEvery { convertTokenValueToFiat(eth, srcTokenValue, AppCurrency.USD) } returns
            FiatValue(BigDecimal("5.26"), "USD")
        coEvery { convertTokenValueToFiat(binu, dstValue, AppCurrency.USD) } returns
            FiatValue(BigDecimal("13.18"), "USD")
        every { mapTokenValueToDecimalUiString(any()) } returns "35900000"
        coEvery { fiatValueToString(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }

        val result =
            createManager()
                .fetchQuote(
                    SwapProvider.SWAPKIT,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    eth,
                    binu,
                    BigInteger.ONE,
                    srcTokenValue,
                    AppCurrency.USD,
                    null,
                    null,
                    BigDecimal.ONE,
                )

        // Displayed destination fiat clamps to the source fiat, not the inflated $13.18 mark.
        result.estimatedDstFiatValue shouldBe "5.26"
        // Ranking still sees the unclamped market value.
        result.estimatedDstFiat.value shouldBe BigDecimal("13.18")
    }

    @Test
    fun `fetchQuote keeps a destination fiat that is below the source fiat`() = runTest {
        // Real price impact / fees legitimately make the destination worth less than the source.
        // That divergence is honest, so it must pass through unclamped instead of being inflated
        // back up to the source fiat.
        val eth = coin(Chain.Ethereum, "ETH", "0xsrc", 18)
        val usdc = coin(Chain.Base, "USDC", "0xdst", 6)
        val srcTokenValue = TokenValue(BigInteger.valueOf(1_000_000_000_000_000_000L), eth)
        val dstValue = TokenValue(BigInteger.valueOf(95_000_000L), usdc)
        val quote =
            SwapQuote.SwapKit(
                expectedDstValue = dstValue,
                fees = TokenValue(BigInteger.ZERO, eth),
                expiredAt = Clock.System.now().plus(5.minutes),
                data = mockk(relaxed = true),
                subProvider = null,
            )

        coEvery { tokenRepository.getNativeToken(any()) } returns eth
        coEvery { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, any()) } returns
            SwapQuoteResult.Native(quote)
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        coEvery { convertTokenValueToFiat(eth, srcTokenValue, AppCurrency.USD) } returns
            FiatValue(BigDecimal("100"), "USD")
        coEvery { convertTokenValueToFiat(usdc, dstValue, AppCurrency.USD) } returns
            FiatValue(BigDecimal("95"), "USD")
        every { mapTokenValueToDecimalUiString(any()) } returns "95"
        coEvery { fiatValueToString(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }

        val result =
            createManager()
                .fetchQuote(
                    SwapProvider.SWAPKIT,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    eth,
                    usdc,
                    BigInteger.ONE,
                    srcTokenValue,
                    AppCurrency.USD,
                    null,
                    null,
                    BigDecimal.ONE,
                )

        result.estimatedDstFiatValue shouldBe "95"
        result.estimatedDstFiat.value shouldBe BigDecimal("95")
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

    @Test
    fun `amountChanges flags a multi-character paste as immediate but typing as not`() = runTest {
        // "" (empty, non-immediate), "1" (single-char type, 0->1 = not a paste),
        // "1000" (1->4 jump = paste -> immediate).
        val result = createManager().amountChanges(flowOf("", "1", "1000")).toList()

        assertEquals(listOf(false, false, true), result)
    }

    @Test
    fun `markImmediateFetch makes the next non-empty change immediate then resets`() = runTest {
        val manager = createManager()
        manager.markImmediateFetch()

        // First non-empty emission consumes the flag (immediate), the next free-typed one does not.
        val result = manager.amountChanges(flowOf("5", "50")).toList()

        assertEquals(listOf(true, false), result)
    }

    @Test
    fun `amountChanges keeps the pending immediate flag across an empty field`() = runTest {
        val manager = createManager()
        manager.markImmediateFetch()

        // The empty emission rides the normal path and must NOT consume the pending immediate flag,
        // so the next real amount still fetches immediately (#4712).
        val result = manager.amountChanges(flowOf("", "9")).toList()

        assertEquals(listOf(false, true), result)
    }

    @Test
    fun `quoteDebounceMillis bypasses the debounce only for immediate changes`() {
        val manager = createManager()

        assertEquals(0L, manager.quoteDebounceMillis(true))
        assertEquals(300L, manager.quoteDebounceMillis(false))
    }

    @Test
    fun `resolveBestQuote maps a typed swap failure to a Failure result`() = runTest {
        // Empty candidates makes fetchBestQuote throw SwapException.SwapIsNotSupported, which must
        // surface as a Failure carrying the mapped form error and the "swapError" tag.
        val result =
            createManager()
                .resolveBestQuote(
                    candidates = emptyList(),
                    src = mockk(relaxed = true),
                    dst = mockk(relaxed = true),
                    srcToken = coin(Chain.Ethereum, "ETH", "0xsrc", 18),
                    dstToken = coin(Chain.Bitcoin, "BTC", "btc", 8),
                    srcTokenValue = BigInteger.ONE,
                    tokenValue = mockk(relaxed = true),
                    currency = AppCurrency.USD,
                    amount = BigDecimal.ONE,
                    selectedSrcTokenTitle = "ETH",
                )

        val failure = assertIs<QuoteResolution.Failure>(result)
        assertEquals("swapError", failure.tag)
        assertEquals(UiText.StringResource(R.string.swap_route_not_available), failure.formError)
    }

    @Test
    fun `resolveBestQuote wraps a fetched quote in a Success result`() = runTest {
        val btc = coin(Chain.Bitcoin, "BTC", "bc1qsrc", 8)
        val eth = coin(Chain.Ethereum, "ETH", "0xdst", 18)
        val thorDst = TokenValue(BigInteger.valueOf(100), eth)
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
        coEvery { tokenRepository.getNativeToken(any()) } returns btc
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal("100"), "USD")
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } returns
            SwapQuoteResult.Native(thorQuote)

        val result =
            createManager()
                .resolveBestQuote(
                    candidates = listOf(QuoteCandidate(SwapProvider.THORCHAIN, null, null)),
                    src = mockk(relaxed = true),
                    dst = mockk(relaxed = true),
                    srcToken = btc,
                    dstToken = eth,
                    srcTokenValue = BigInteger.ONE,
                    tokenValue = TokenValue(BigInteger.ONE, btc),
                    currency = AppCurrency.USD,
                    amount = BigDecimal.ONE,
                    selectedSrcTokenTitle = "BTC",
                )

        val success = assertIs<QuoteResolution.Success>(result)
        assertEquals(SwapProvider.THORCHAIN, success.best.candidate.provider)
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
