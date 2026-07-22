@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.chains.helpers.SolanaSwap
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
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
import com.vultisig.wallet.data.repositories.swap.SwapQuoteRequest
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
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertFailsWith
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
    fun `fetchBestQuote picks the highest net output, not the candidate order`() = runTest {
        // The provider table hands candidates back in a fixed set order (THORCHAIN before SWAPKIT
        // on Bitcoin), but selection is `maxBy { comparableDstFiat }` — the route the user receives
        // the most on. Pin that: SWAPKIT is listed second yet prices the dst higher, so it must
        // win.
        // This is what makes SwapKit reachable on any BTC pair it out-prices, regardless of where
        // it
        // sits in the eligibility set.
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
        assertEquals(BigDecimal("200"), best.result.comparableDstFiat)
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
        // Ranking still sees the unclamped market value (SwapKit dstAmount is already net).
        result.comparableDstFiat shouldBe BigDecimal("13.18")
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
        result.comparableDstFiat shouldBe BigDecimal("95")
    }

    @Test
    fun `fetchBestQuote prefers higher-priority provider on a near-tie within the 50bps band`() =
        runTest {
            // THORChain (priority 0) prices the dst slightly lower than SwapKit (priority 2), but
            // within the 50bps band (99.7 vs 100, floor = 99.5). Both are in-band, so the banded
            // layer tilts to the higher-priority THORChain instead of the marginally larger output.
            val best = rankTwoProviders(thorFiat = "99.7", swapKitFiat = "100")

            assertEquals(SwapProvider.THORCHAIN, best.candidate.provider)
            assertEquals(BigDecimal("99.7"), best.result.comparableDstFiat)
        }

    @Test
    fun `fetchBestQuote keeps the materially-better quote when it is outside the 50bps band`() =
        runTest {
            // THORChain prices 0.8% below SwapKit (99.2 vs 100, floor = 99.5), outside the band, so
            // the priority preference does not apply and the better-rate SwapKit wins.
            val best = rankTwoProviders(thorFiat = "99.2", swapKitFiat = "100")

            assertEquals(SwapProvider.SWAPKIT, best.candidate.provider)
            assertEquals(BigDecimal("100"), best.result.comparableDstFiat)
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

    // selectBestQuote — net-output ranking, banded provider preference, and the in-band lower-gas
    // tie-break. Each case pins one boundary of the rule (#5011).

    @Test
    fun `selectBestQuote picks the highest net output`() {
        // THORChain leads the eligibility order for ETH/USDC, but the candidate with the highest
        // net
        // output must win regardless of provider order.
        val best =
            createManager()
                .selectBestQuote(
                    listOf(
                        rankable(SwapProvider.THORCHAIN, "0.0029"),
                        rankable(SwapProvider.ONEINCH, "0.0030"),
                        rankable(SwapProvider.LIFI, "0.0029"),
                    )
                )

        assertEquals(SwapProvider.ONEINCH, best.candidate.provider)
    }

    @Test
    fun `selectBestQuote prefers the higher-priority provider within the band`() {
        // 1inch's net is marginally higher but THORChain sits inside the 50bps band (floor
        // 0.02985),
        // so the higher-priority THORChain wins. Neither exposes gas, so the lower-gas check is
        // skipped and selection falls to provider priority.
        val best =
            createManager()
                .selectBestQuote(
                    listOf(
                        rankable(SwapProvider.ONEINCH, "0.03"),
                        rankable(SwapProvider.THORCHAIN, "0.0299"),
                    )
                )

        assertEquals(SwapProvider.THORCHAIN, best.candidate.provider)
    }

    @Test
    fun `selectBestQuote treats the exact band floor as in-band`() {
        // A quote exactly at best * 0.995 (0.03 -> 0.02985) is included and, being higher priority,
        // wins.
        val best =
            createManager()
                .selectBestQuote(
                    listOf(
                        rankable(SwapProvider.ONEINCH, "0.03"),
                        rankable(SwapProvider.THORCHAIN, "0.02985"),
                    )
                )

        assertEquals(SwapProvider.THORCHAIN, best.candidate.provider)
    }

    @Test
    fun `selectBestQuote keeps the better rate just outside the band`() {
        // THORChain at 0.0296 is below the floor (0.02985), so only 1inch is in band and the better
        // rate wins — the priority preference must not reach outside the band.
        val best =
            createManager()
                .selectBestQuote(
                    listOf(
                        rankable(SwapProvider.ONEINCH, "0.03"),
                        rankable(SwapProvider.THORCHAIN, "0.0296"),
                    )
                )

        assertEquals(SwapProvider.ONEINCH, best.candidate.provider)
    }

    @Test
    fun `selectBestQuote prefers Jupiter over LI-FI and SwapKit for an on-Solana pair`() {
        // SOL↔SPL / SPL↔SPL is the only candidate set Jupiter ever appears in: against LI.FI and a
        // SwapKit/NEAR cross-chain route. The spec wants Jupiter (the native Solana DEX aggregator,
        // no markup) preferred there (#5053). With equal net output all three are in band and none
        // expose source gas, so provider priority decides — Jupiter must outrank BOTH LI.FI and
        // SwapKit (whose higher cross-chain priority must not steal an on-Solana swap).
        val best =
            createManager()
                .selectBestQuote(
                    listOf(
                        rankable(SwapProvider.SWAPKIT, "0.03"),
                        rankable(SwapProvider.LIFI, "0.03"),
                        rankable(SwapProvider.JUPITER, "0.03"),
                    )
                )

        assertEquals(SwapProvider.JUPITER, best.candidate.provider)
    }

    @Test
    fun `selectBestQuote prefers the lower-gas route among in-band EVM aggregators`() {
        // KyberSwap has the marginally higher net AND higher priority, but burns more source gas;
        // 1inch is in band (floor 0.02985) with cheaper gas. The lower-gas quote wins, beating both
        // the higher output and the higher provider priority.
        val best =
            createManager()
                .selectBestQuote(
                    listOf(
                        rankable(SwapProvider.KYBER, "0.03", sourceGasWei = 6_000_000_000_000_000),
                        rankable(
                            SwapProvider.ONEINCH,
                            "0.02995",
                            sourceGasWei = 3_000_000_000_000_000,
                        ),
                    )
                )

        assertEquals(SwapProvider.ONEINCH, best.candidate.provider)
    }

    @Test
    fun `selectBestQuote keeps the materially-better rate even when it costs more gas`() {
        // A rate outside the band must win even with far more source gas — the lower-gas tie-break
        // only applies among in-band, economically-tied quotes.
        val best =
            createManager()
                .selectBestQuote(
                    listOf(
                        rankable(SwapProvider.KYBER, "0.03", sourceGasWei = 12_000_000_000_000_000),
                        rankable(
                            SwapProvider.ONEINCH,
                            "0.029",
                            sourceGasWei = 2_000_000_000_000_000,
                        ),
                    )
                )

        assertEquals(SwapProvider.KYBER, best.candidate.provider)
    }

    @Test
    fun `selectBestQuote does not let a gas-unknown quote win the lower-gas tie-break`() {
        // THORChain exposes no source gas, so the lower-gas check cannot fire even though 1inch has
        // a known (cheap) gas. Both are in band, so selection falls to provider priority and the
        // higher-priority THORChain wins.
        val best =
            createManager()
                .selectBestQuote(
                    listOf(
                        rankable(
                            SwapProvider.ONEINCH,
                            "0.03",
                            sourceGasWei = 3_000_000_000_000_000,
                        ),
                        rankable(SwapProvider.THORCHAIN, "0.0299"),
                    )
                )

        assertEquals(SwapProvider.THORCHAIN, best.candidate.provider)
    }

    @Test
    fun `selectBestQuote winner is independent of candidate order for mixed-gas in-band quotes`() {
        // A gas-unknown quote in band alongside two gas-exposing aggregators must resolve to the
        // same winner regardless of order — ranking is a single total order, not an order-sensitive
        // pairwise comparison.
        val quotes =
            listOf(
                rankable(SwapProvider.SWAPKIT, "0.0299"),
                rankable(SwapProvider.KYBER, "0.0299", sourceGasWei = 6_000_000_000_000_000),
                rankable(SwapProvider.ONEINCH, "0.03", sourceGasWei = 3_000_000_000_000_000),
            )
        val manager = createManager()

        val forward = manager.selectBestQuote(quotes).candidate.provider
        val reversed = manager.selectBestQuote(quotes.reversed()).candidate.provider

        assertEquals(SwapProvider.SWAPKIT, forward)
        assertEquals(forward, reversed)
    }

    @Test
    fun `selectBestQuote returns the only quote`() {
        val best = createManager().selectBestQuote(listOf(rankable(SwapProvider.LIFI, "0.03")))

        assertEquals(SwapProvider.LIFI, best.candidate.provider)
    }

    @Test
    fun `fetchQuote ranks LI_FI on its net dstAmount without re-subtracting the integrator fee`() =
        runTest {
            // LI.FI's quoted dstAmount is already net of its integrator fee (the fee is deducted
            // from
            // the quoted toAmount), so the ranking metric is the plain market value —
            // re-subtracting
            // the fee would double-count. The catch-all prices the fee at a non-zero 0.5 so any
            // stray
            // subtraction would surface; the dst market value is stubbed specifically.
            val eth = coin(Chain.Ethereum, "ETH", "0xsrc", 18)
            val usdc = coin(Chain.Ethereum, "USDC", "0xdst", 6)
            val dstValue = TokenValue(BigInteger.valueOf(1_000_000), usdc)
            coEvery { tokenRepository.getNativeToken(any()) } returns eth
            coEvery { swapQuoteRepository.getQuote(SwapProvider.LIFI, any()) } returns
                SwapQuoteResult.Evm(evmQuote(dstAmount = "1000000"))
            coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
                FiatValue(BigDecimal("0.5"), "USD")
            coEvery { convertTokenValueToFiat(any(), dstValue, AppCurrency.USD) } returns
                FiatValue(BigDecimal("100"), "USD")
            every { mapTokenValueToDecimalUiString(any()) } returns "0"

            val result =
                createManager()
                    .fetchQuote(
                        provider = SwapProvider.LIFI,
                        src = mockk(relaxed = true),
                        dst = mockk(relaxed = true),
                        srcToken = eth,
                        dstToken = usdc,
                        srcTokenValue = BigInteger.ONE,
                        tokenValue = TokenValue(BigInteger.ONE, eth),
                        currency = AppCurrency.USD,
                        vultBPSDiscount = null,
                        referral = null,
                        amount = BigDecimal.ONE,
                    )

            // Market value passes through untouched; the 0.5 fee is not subtracted again.
            result.comparableDstFiat shouldBe BigDecimal("100")
        }

    @Test
    fun `fetchQuote does not subtract THORChain fees from the comparable net output`() = runTest {
        // THORChain expectedAmountOut is already net of affiliate + outbound fees, so subtracting
        // the displayed swap fee again would understate it and wrongly tilt selection to an
        // aggregator (#5011). Non-zero fee fiat would surface any stray subtraction.
        val btc = coin(Chain.Bitcoin, "BTC", "bc1qsrc", 8)
        val eth = coin(Chain.Ethereum, "ETH", "0xdst", 18)
        val thorDst = TokenValue(BigInteger.valueOf(100), eth)
        val thorQuote =
            SwapQuote.ThorChain(
                expectedDstValue = thorDst,
                fees = TokenValue(BigInteger.valueOf(7), eth),
                expiredAt = Clock.System.now().plus(5.minutes),
                recommendedMinTokenValue = TokenValue(BigInteger.ZERO, eth),
                data =
                    THORChainSwapQuote(
                        dustThreshold = null,
                        expectedAmountOut = "100",
                        expiry = BigInteger.ZERO,
                        fees = Fees(affiliate = "3", asset = "0", outbound = "4", total = "7"),
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
        coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } returns
            SwapQuoteResult.Native(thorQuote)
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal("5"), "USD")
        coEvery { convertTokenValueToFiat(any(), thorDst, any()) } returns
            FiatValue(BigDecimal("100"), "USD")
        every { mapTokenValueToDecimalUiString(any()) } returns "0"

        val result =
            createManager()
                .fetchQuote(
                    provider = SwapProvider.THORCHAIN,
                    src = mockk(relaxed = true),
                    dst = mockk(relaxed = true),
                    srcToken = btc,
                    dstToken = eth,
                    srcTokenValue = BigInteger.ONE,
                    tokenValue = TokenValue(BigInteger.ONE, btc),
                    currency = AppCurrency.USD,
                    vultBPSDiscount = null,
                    referral = null,
                    amount = BigDecimal.ONE,
                )

        // Market value passes through untouched (100), and THORChain exposes no source gas.
        result.comparableDstFiat shouldBe BigDecimal("100")
        result.sourceGasWei shouldBe null
    }

    @Test
    fun `formatAffiliatePercent sources the net rate from the sent bps`() {
        // Base 50 bps with no discount, reduced by the VULT discount, clamped at zero. This is the
        // Swap Fee row title (#5358).
        formatAffiliatePercent(null) shouldBe "0.50%"
        formatAffiliatePercent(0) shouldBe "0.50%"
        formatAffiliatePercent(20) shouldBe "0.30%"
        formatAffiliatePercent(50) shouldBe "0.00%" // Ultimate tier: full discount.
        formatAffiliatePercent(60) shouldBe "0.00%" // Over-discount never goes negative.
    }

    @Test
    fun `every provider quotes the same base affiliate rate as the one displayed`() {
        // formatAffiliatePercent renders a single rate for all providers, so a divergence in any
        // provider's base bps would silently overstate/understate the Swap Fee row. THORChain's
        // rate lives in another module and cannot derive from BASE_AFFILIATE_FEE_BPS, so assert it
        // here — drift fails on the PR rather than on a user's device.
        THORChainSwaps.AFFILIATE_FEE_RATE_BP shouldBe BASE_AFFILIATE_FEE_BPS
    }

    @Test
    fun `fetchQuote marks 1inch fee as included in the rate rather than showing gas`() = runTest {
        // 1inch never returns the affiliate fee separately — it is baked into the quoted rate. The
        // Swap Fee row must read "included in quoted rate" (empty fee text + flag) and contribute
        // nothing to the total, never gasPrice × gas mislabeled as the swap fee (#5358, #5334).
        val eth = coin(Chain.Ethereum, "ETH", "0xsrc", 18)
        val usdc = coin(Chain.Ethereum, "USDC", "0xdst", 6)
        coEvery { tokenRepository.getNativeToken(any()) } returns eth
        coEvery { swapQuoteRepository.getQuote(SwapProvider.ONEINCH, any()) } returns
            SwapQuoteResult.Evm(
                evmQuote(dstAmount = "1000000", gas = 150_000, gasPrice = "20000000000")
            )
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal("2"), "USD")
        every { mapTokenValueToDecimalUiString(any()) } returns "0"

        val result =
            createManager()
                .fetchQuote(
                    provider = SwapProvider.ONEINCH,
                    src = mockk(relaxed = true),
                    dst = mockk(relaxed = true),
                    srcToken = eth,
                    dstToken = usdc,
                    srcTokenValue = BigInteger.ONE,
                    tokenValue = TokenValue(BigInteger.ONE, eth),
                    currency = AppCurrency.USD,
                    vultBPSDiscount = null,
                    referral = null,
                    amount = BigDecimal.ONE,
                )

        result.swapFeeIncludedInRate shouldBe true
        result.feeText shouldBe ""
        result.swapFeeFiat shouldBe FiatValue(BigDecimal.ZERO, "USD")
        result.swapFeePercent shouldBe "0.50%"
    }

    @Test
    fun `fetchQuote 1inch swap fee percent reflects the VULT discount`() = runTest {
        val eth = coin(Chain.Ethereum, "ETH", "0xsrc", 18)
        val usdc = coin(Chain.Ethereum, "USDC", "0xdst", 6)
        coEvery { tokenRepository.getNativeToken(any()) } returns eth
        coEvery { swapQuoteRepository.getQuote(SwapProvider.ONEINCH, any()) } returns
            SwapQuoteResult.Evm(evmQuote(dstAmount = "1000000"))
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        every { mapTokenValueToDecimalUiString(any()) } returns "0"

        val result =
            createManager()
                .fetchQuote(
                    provider = SwapProvider.ONEINCH,
                    src = mockk(relaxed = true),
                    dst = mockk(relaxed = true),
                    srcToken = eth,
                    dstToken = usdc,
                    srcTokenValue = BigInteger.ONE,
                    tokenValue = TokenValue(BigInteger.ONE, eth),
                    currency = AppCurrency.USD,
                    vultBPSDiscount = 20,
                    referral = null,
                    amount = BigDecimal.ONE,
                )

        result.swapFeePercent shouldBe "0.30%"
        result.swapFeeIncludedInRate shouldBe true
    }

    @Test
    fun `fetchQuote sources THORChain swap fee percent from the sent bps, itemized not baked`() =
        runTest {
            val btc = coin(Chain.Bitcoin, "BTC", "bc1qsrc", 8)
            val eth = coin(Chain.Ethereum, "ETH", "0xdst", 18)
            val thorDst = TokenValue(BigInteger.valueOf(100), eth)
            val thorQuote =
                SwapQuote.ThorChain(
                    expectedDstValue = thorDst,
                    fees = TokenValue(BigInteger.valueOf(7), eth),
                    expiredAt = Clock.System.now().plus(5.minutes),
                    recommendedMinTokenValue = TokenValue(BigInteger.ZERO, eth),
                    data =
                        THORChainSwapQuote(
                            dustThreshold = null,
                            expectedAmountOut = "100",
                            expiry = BigInteger.ZERO,
                            fees = Fees(affiliate = "3", asset = "0", outbound = "4", total = "7"),
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
            coEvery { swapQuoteRepository.getQuote(SwapProvider.THORCHAIN, any()) } returns
                SwapQuoteResult.Native(thorQuote)
            coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
                FiatValue(BigDecimal("5"), "USD")
            every { mapTokenValueToDecimalUiString(any()) } returns "0"

            val result =
                createManager()
                    .fetchQuote(
                        provider = SwapProvider.THORCHAIN,
                        src = mockk(relaxed = true),
                        dst = mockk(relaxed = true),
                        srcToken = btc,
                        dstToken = eth,
                        srcTokenValue = BigInteger.ONE,
                        tokenValue = TokenValue(BigInteger.ONE, btc),
                        currency = AppCurrency.USD,
                        vultBPSDiscount = null,
                        referral = null,
                        amount = BigDecimal.ONE,
                    )

            // Percentage from the sent bps, and THORChain itemizes a real fee (never baked).
            result.swapFeePercent shouldBe "0.50%"
            result.swapFeeIncludedInRate shouldBe false
        }

    @Test
    fun `fetchQuote exposes source gas as gas times gasPrice for an EVM aggregator`() = runTest {
        // gas 150_000 × gasPrice 20 gwei = 3e15 wei — the value the in-band lower-gas tie-break
        // compares.
        val eth = coin(Chain.Ethereum, "ETH", "0xsrc", 18)
        val usdc = coin(Chain.Ethereum, "USDC", "0xdst", 6)
        coEvery { tokenRepository.getNativeToken(any()) } returns eth
        coEvery { swapQuoteRepository.getQuote(SwapProvider.ONEINCH, any()) } returns
            SwapQuoteResult.Evm(
                evmQuote(dstAmount = "1000000", gas = 150_000, gasPrice = "20000000000")
            )
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        every { mapTokenValueToDecimalUiString(any()) } returns "0"

        val result =
            createManager()
                .fetchQuote(
                    provider = SwapProvider.ONEINCH,
                    src = mockk(relaxed = true),
                    dst = mockk(relaxed = true),
                    srcToken = eth,
                    dstToken = usdc,
                    srcTokenValue = BigInteger.ONE,
                    tokenValue = TokenValue(BigInteger.ONE, eth),
                    currency = AppCurrency.USD,
                    vultBPSDiscount = null,
                    referral = null,
                    amount = BigDecimal.ONE,
                )

        result.sourceGasWei shouldBe BigInteger("3000000000000000")
    }

    @Test
    fun `fetchQuote reports no source gas when the aggregator returns a zero gas estimate`() =
        runTest {
            // A zero gas estimate (which some aggregators return) must read as gas-unknown so it
            // never wins the cheapest-gas tie-break.
            val eth = coin(Chain.Ethereum, "ETH", "0xsrc", 18)
            val usdc = coin(Chain.Ethereum, "USDC", "0xdst", 6)
            coEvery { tokenRepository.getNativeToken(any()) } returns eth
            coEvery { swapQuoteRepository.getQuote(SwapProvider.ONEINCH, any()) } returns
                SwapQuoteResult.Evm(
                    evmQuote(dstAmount = "1000000", gas = 0, gasPrice = "20000000000")
                )
            coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
                FiatValue(BigDecimal.ZERO, "USD")
            every { mapTokenValueToDecimalUiString(any()) } returns "0"

            val result =
                createManager()
                    .fetchQuote(
                        provider = SwapProvider.ONEINCH,
                        src = mockk(relaxed = true),
                        dst = mockk(relaxed = true),
                        srcToken = eth,
                        dstToken = usdc,
                        srcTokenValue = BigInteger.ONE,
                        tokenValue = TokenValue(BigInteger.ONE, eth),
                        currency = AppCurrency.USD,
                        vultBPSDiscount = null,
                        referral = null,
                        amount = BigDecimal.ONE,
                    )

            result.sourceGasWei shouldBe null
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

    @Test
    fun `fetchQuote forwards the user-set slippage to the Jupiter request`() = runTest {
        // The Jupiter request is built inside SwapQuoteManager; pin that it now carries the chosen
        // slippage so a user-set tolerance actually reaches the Solana quote (the data-layer wiring
        // is moot if the caller never populates it). Throw after capture to stay off the fee path.
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        val requestSlot = slot<SwapQuoteRequest>()
        coEvery { swapQuoteRepository.getQuote(SwapProvider.JUPITER, capture(requestSlot)) } throws
            SwapException.SwapRouteNotAvailable("stop after capture")

        assertFailsWith<SwapException.SwapRouteNotAvailable> {
            fetchJupiterQuote(createManager(), slippageBps = 250)
        }

        assertEquals(250, requestSlot.captured.slippageBps)
    }

    @Test
    fun `Jupiter quotes that differ only in slippage do not collide in the cache`() = runTest {
        // Slippage is part of the quote cache key, so a re-fetch after a slippage change must miss
        // and re-quote rather than serve the stale tolerance. Two identical-slippage calls share
        // one
        // network call; a third at a different slippage adds a second — three calls, two fetches.
        val sol = coin(Chain.Solana, "SOL", "SoLsrc", 9)
        coEvery { tokenRepository.getNativeToken(any()) } returns sol
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { swapQuoteRepository.getQuote(SwapProvider.JUPITER, any()) } returns
            SwapQuoteResult.Evm(jupiterEvmQuote())

        val manager = createManager()
        fetchJupiterQuote(manager, slippageBps = 100)
        fetchJupiterQuote(manager, slippageBps = 100)
        fetchJupiterQuote(manager, slippageBps = 200)

        coVerify(exactly = 2) { swapQuoteRepository.getQuote(SwapProvider.JUPITER, any()) }
    }

    @Test
    fun `fetchQuote forwards the user-set slippage to the SwapKit request`() = runTest {
        // Pin that the chosen tolerance reaches the request the source forwards to SwapKit's
        // /v3/quote. Throw after capture to stay off the fee path.
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        val requestSlot = slot<SwapQuoteRequest>()
        coEvery { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, capture(requestSlot)) } throws
            SwapException.SwapRouteNotAvailable("stop after capture")

        assertFailsWith<SwapException.SwapRouteNotAvailable> {
            fetchSwapKitQuote(createManager(), slippageBps = 250)
        }

        assertEquals(250, requestSlot.captured.slippageBps)
    }

    @Test
    fun `SwapKit quotes that differ only in slippage do not collide in the cache`() = runTest {
        // Slippage is part of the SwapKit cache key, so a re-fetch after a tolerance change must
        // miss and re-quote rather than serve a route built for the old tolerance. Two
        // identical-slippage calls share one network call; a third at a different slippage adds a
        // second — three calls, two fetches.
        val eth = coin(Chain.Ethereum, "ETH", "0xsrc", 18)
        coEvery { tokenRepository.getNativeToken(any()) } returns eth
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, any()) } returns
            SwapQuoteResult.Evm(jupiterEvmQuote())

        val manager = createManager()
        fetchSwapKitQuote(manager, slippageBps = 100)
        fetchSwapKitQuote(manager, slippageBps = 100)
        fetchSwapKitQuote(manager, slippageBps = 200)

        coVerify(exactly = 2) { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, any()) }
    }

    @Test
    fun `fetchQuote drops a Jupiter Solana route that exceeds the account-lock cap`() = runTest {
        // A built tx locking more than Solana's 64-account cap is unbroadcastable
        // (TooManyAccountLocks), so the guard must fail this provider's fetch instead of signing a
        // doomed tx (#5131).
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        coEvery { swapQuoteRepository.getQuote(SwapProvider.JUPITER, any()) } returns
            SwapQuoteResult.Evm(jupiterEvmQuote())
        mockkObject(SolanaSwap.Companion)
        try {
            every { SolanaSwap.countAccountLocks(any<String>()) } returns
                SolanaSwap.MAX_TX_ACCOUNT_LOCKS + 2

            assertFailsWith<SwapException.SwapRouteNotAvailable> {
                fetchJupiterQuote(createManager(), slippageBps = 100)
            }
        } finally {
            unmockkObject(SolanaSwap.Companion)
        }
    }

    @Test
    fun `fetchQuote keeps a Jupiter Solana route when the lock count cannot be decoded`() =
        runTest {
            // Fail-open: an undecodable tx must not abort the fetch — the route proceeds rather
            // than
            // being dropped on a tx the guard can't positively confirm is over the cap (#5131).
            coEvery { tokenRepository.getNativeToken(any()) } returns
                coin(Chain.Solana, "SOL", "SoLsrc", 9)
            coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
                FiatValue(BigDecimal.ZERO, "USD")
            every { mapTokenValueToDecimalUiString(any()) } returns "0"
            coEvery { swapQuoteRepository.getQuote(SwapProvider.JUPITER, any()) } returns
                SwapQuoteResult.Evm(jupiterEvmQuote())
            mockkObject(SolanaSwap.Companion)
            try {
                every { SolanaSwap.countAccountLocks(any<String>()) } throws
                    IllegalStateException("Can't decode swap transaction")

                assertNotNull(fetchJupiterQuote(createManager(), slippageBps = 100))
            } finally {
                unmockkObject(SolanaSwap.Companion)
            }
        }

    @Test
    fun `fetchQuote drops a SwapKit Solana-source route that exceeds the account-lock cap`() =
        runTest {
            // SwapKit stages a Solana-source route (Chainflip/NEAR Intents) on the same EVM
            // envelope, so the over-lock guard must run for it too — otherwise a >64-lock tx can
            // still stage and sign (#5131).
            coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
                FiatValue(BigDecimal.ZERO, "USD")
            coEvery { swapQuoteRepository.getQuote(SwapProvider.SWAPKIT, any()) } returns
                SwapQuoteResult.Evm(jupiterEvmQuote())
            mockkObject(SolanaSwap.Companion)
            try {
                every { SolanaSwap.countAccountLocks(any<String>()) } returns
                    SolanaSwap.MAX_TX_ACCOUNT_LOCKS + 2

                assertFailsWith<SwapException.SwapRouteNotAvailable> {
                    fetchSolanaSwapKitQuote(createManager())
                }
            } finally {
                unmockkObject(SolanaSwap.Companion)
            }
        }

    private suspend fun fetchSolanaSwapKitQuote(manager: SwapQuoteManager) =
        manager.fetchQuote(
            provider = SwapProvider.SWAPKIT,
            src = mockk(relaxed = true),
            dst = mockk(relaxed = true),
            srcToken = coin(Chain.Solana, "SOL", "SoLsrc", 9),
            dstToken = coin(Chain.Ethereum, "USDC", "0xdst", 6),
            srcTokenValue = BigInteger.ONE,
            tokenValue = TokenValue(BigInteger.ONE, coin(Chain.Solana, "SOL", "SoLsrc", 9)),
            currency = AppCurrency.USD,
            vultBPSDiscount = null,
            referral = null,
            amount = BigDecimal.ONE,
            slippageBps = 100,
        )

    private suspend fun fetchSwapKitQuote(manager: SwapQuoteManager, slippageBps: Int?) =
        manager.fetchQuote(
            provider = SwapProvider.SWAPKIT,
            src = mockk(relaxed = true),
            dst = mockk(relaxed = true),
            srcToken = coin(Chain.Ethereum, "ETH", "0xsrc", 18),
            dstToken = coin(Chain.Ethereum, "USDC", "0xdst", 6),
            srcTokenValue = BigInteger.ONE,
            tokenValue = TokenValue(BigInteger.ONE, coin(Chain.Ethereum, "ETH", "0xsrc", 18)),
            currency = AppCurrency.USD,
            vultBPSDiscount = null,
            referral = null,
            amount = BigDecimal.ONE,
            slippageBps = slippageBps,
        )

    private suspend fun fetchJupiterQuote(manager: SwapQuoteManager, slippageBps: Int?) =
        manager.fetchQuote(
            provider = SwapProvider.JUPITER,
            src = mockk(relaxed = true),
            dst = mockk(relaxed = true),
            srcToken = coin(Chain.Solana, "SOL", "SoLsrc", 9),
            dstToken = coin(Chain.Solana, "USDC", "UsdcDst", 6),
            srcTokenValue = BigInteger.ONE,
            tokenValue = TokenValue(BigInteger.ONE, coin(Chain.Solana, "SOL", "SoLsrc", 9)),
            currency = AppCurrency.USD,
            vultBPSDiscount = null,
            referral = null,
            amount = BigDecimal.ONE,
            slippageBps = slippageBps,
        )

    private fun jupiterEvmQuote() =
        evmQuote(dstAmount = "1000", gas = 0, gasPrice = "0", data = "AQID")

    private fun evmQuote(
        dstAmount: String,
        gas: Long = 200_000L,
        gasPrice: String = "20000000000",
        data: String = "0x",
    ) =
        EVMSwapQuoteJson(
            dstAmount = dstAmount,
            tx =
                OneInchSwapTxJson(
                    from = "",
                    to = "",
                    gas = gas,
                    data = data,
                    value = "0",
                    gasPrice = gasPrice,
                    swapFee = "0",
                    swapFeeTokenContract = "",
                ),
        )

    /** A pre-fetched [BestQuote] with a known net output (and optional source gas) for ranking. */
    private fun rankable(provider: SwapProvider, netFiat: String, sourceGasWei: Long? = null) =
        BestQuote(
            candidate = QuoteCandidate(provider, vultBPSDiscount = null, referral = null),
            result =
                QuoteFetchResult(
                    quote = mockk(relaxed = true),
                    provider = provider,
                    providerUiText = UiText.DynamicString(provider.name),
                    srcFiatValueText = "",
                    estimatedDstTokenValue = "",
                    estimatedDstFiatValue = "",
                    comparableDstFiat = BigDecimal(netFiat),
                    feeText = "",
                    swapFeeFiat = FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker),
                    sourceGasWei = sourceGasWei?.let { BigInteger.valueOf(it) },
                ),
        )

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
