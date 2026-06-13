package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.LiFiChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapQuote.Companion.expiredAfter
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.swap.SwapQuoteRequest
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import com.vultisig.wallet.data.repositories.swap.convertToTokenValue
import com.vultisig.wallet.data.usecases.ConvertTokenToToken
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.SearchTokenUseCase
import com.vultisig.wallet.data.utils.thorswapMultiplier
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import timber.log.Timber

internal data class QuoteFetchResult(
    val quote: SwapQuote,
    val provider: SwapProvider,
    val providerUiText: UiText,
    val srcFiatValueText: String,
    val estimatedDstTokenValue: String,
    // Displayed destination fiat: market value clamped to the source fiat (#4878).
    val estimatedDstFiatValue: String,
    // Unclamped market value (dstAmount × dstMarketPrice) used only for cross-provider ranking.
    val estimatedDstFiat: FiatValue,
    val feeText: String,
    val swapFeeFiat: FiatValue,
    val outboundFeeText: String? = null,
    val swapFeePercent: String? = null,
)

internal data class QuoteCandidate(
    val provider: SwapProvider,
    val vultBPSDiscount: Int?,
    val referral: String?,
)

internal data class BestQuote(val candidate: QuoteCandidate, val result: QuoteFetchResult)

/**
 * Outcome of resolving the best quote for a pair: either a [Success] holding the winning
 * [BestQuote], or a [Failure] whose typed swap error has already been mapped to a renderable
 * [Failure.formError] so the ViewModel only has to surface it.
 */
internal sealed interface QuoteResolution {
    /** A winning quote was fetched. */
    data class Success(val best: BestQuote) : QuoteResolution

    /** The fetch failed; [formError] is the mapped message, [cause]/[tag] are for logging. */
    data class Failure(val formError: UiText, val cause: Throwable, val tag: String) :
        QuoteResolution
}

/**
 * Display-only indicative destination estimate derived from cached spot prices, shown greyed while
 * the firm quote is still resolving so the destination field never blanks (#4712). It is never used
 * to build a signed transaction — only the firm [SwapQuote] gates Continue.
 */
internal data class IndicativeQuote(
    val estimatedDstTokenValue: String,
    val estimatedDstFiatValue: String,
)

internal class SwapQuoteManager
@Inject
constructor(
    private val swapQuoteRepository: SwapQuoteRepository,
    private val tokenRepository: TokenRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToString: FiatValueToStringMapper,
    private val searchToken: SearchTokenUseCase,
    private val convertTokenToTokenUseCase: ConvertTokenToToken,
) {

    private val quoteCache = QuoteCache()

    // Set true right before a programmatic amount change (percentage / Max) so the next non-empty
    // amount emission skips the typing debounce and fetches a quote immediately (#4712). Reset as
    // soon as it is consumed by the quote flow so it never leaks into subsequent free typing. Only
    // ever touched on the main thread (UI callbacks + the Main-dispatched quote flow).
    private var fetchQuoteImmediately = false

    // Length of the previous source-amount text, used to distinguish a paste (multi-character jump)
    // from free typing so a paste also fetches immediately (#4712).
    private var lastSrcAmountLength = 0

    /**
     * Marks the next non-empty source-amount change to bypass the typing debounce, so an explicit
     * percentage / Max tap fetches a quote immediately instead of waiting it out (#4712). Call on
     * the main thread, before mutating the amount text, so the resulting emission is already
     * marked.
     */
    fun markImmediateFetch() {
        fetchQuoteImmediately = true
    }

    /**
     * Maps a raw source-amount [textFlow] to a flow emitting, per change, whether the quote should
     * fetch immediately (percentage / Max / paste) rather than waiting out the typing debounce
     * (#4712). A paste is detected as a multi-character length jump (tracked across empties so a
     * clear-then-paste still counts). An empty field rides the normal path and deliberately leaves
     * any pending immediate flag intact for the next real amount.
     */
    fun amountChanges(textFlow: Flow<CharSequence>): Flow<Boolean> =
        textFlow
            .map { it.toString() }
            .map { text ->
                val isPaste = text.length - lastSrcAmountLength > 1
                lastSrcAmountLength = text.length
                text to isPaste
            }
            .map { (text, isPaste) ->
                if (text.isEmpty()) return@map false
                val immediate = fetchQuoteImmediately || isPaste
                fetchQuoteImmediately = false
                immediate
            }

    /**
     * Debounce window for a quote fetch: 0ms for an [immediate] change (percentage / Max / paste)
     * so it fires at once, [QUOTE_DEBOUNCE_MS] for free typing so rapid edits coalesce.
     */
    fun quoteDebounceMillis(immediate: Boolean): Long = if (immediate) 0L else QUOTE_DEBOUNCE_MS

    /**
     * Compute an instant, indicative destination amount from already-cached spot prices: `dst =
     * srcAmount × priceSrc / priceDst`. Cached-only by design — it must never hit the network
     * (that's the firm quote's job), so a cold price simply returns null and the caller keeps the
     * previous value visible. The result reuses the same formatters as the firm quote so the greyed
     * placeholder reads identically to the value that replaces it.
     */
    suspend fun computeIndicativeQuote(
        srcToken: Coin,
        dstToken: Coin,
        amount: BigDecimal,
        currency: AppCurrency,
    ): IndicativeQuote? {
        if (amount <= BigDecimal.ZERO || srcToken.id == dstToken.id) return null

        val srcPrice = tokenPriceRepository.getCachedPrice(srcToken.id, currency) ?: return null
        val dstPrice = tokenPriceRepository.getCachedPrice(dstToken.id, currency) ?: return null
        if (srcPrice <= BigDecimal.ZERO || dstPrice <= BigDecimal.ZERO) return null

        val srcFiatValue = amount.multiply(srcPrice)
        val dstDecimal =
            srcFiatValue.divide(
                dstPrice,
                dstToken.decimal.coerceAtMost(MAX_INDICATIVE_DECIMALS),
                RoundingMode.DOWN,
            )
        val dstTokenValue =
            TokenValue(
                value = dstDecimal.movePointRight(dstToken.decimal).toBigInteger(),
                token = dstToken,
            )

        return IndicativeQuote(
            estimatedDstTokenValue = mapTokenValueToDecimalUiString(dstTokenValue),
            estimatedDstFiatValue =
                fiatValueToString(FiatValue(value = srcFiatValue, currency = currency.ticker)),
        )
    }

    suspend fun fetchQuote(
        provider: SwapProvider,
        src: SendSrc,
        dst: SendSrc,
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        currency: AppCurrency,
        vultBPSDiscount: Int?,
        referral: String?,
        amount: BigDecimal,
    ): QuoteFetchResult {
        val srcNativeToken = tokenRepository.getNativeToken(srcToken.chain.id)

        val srcFiatValue = convertTokenValueToFiat(srcToken, tokenValue, currency)
        val srcFiatValueText = fiatValueToString(srcFiatValue)

        val (quote, providerText) =
            when (provider) {
                SwapProvider.MAYA,
                SwapProvider.THORCHAIN ->
                    fetchThorMayaQuote(
                        provider,
                        src,
                        dst,
                        srcToken,
                        dstToken,
                        srcTokenValue,
                        tokenValue,
                        vultBPSDiscount,
                        referral,
                        amount,
                    )

                SwapProvider.KYBER ->
                    fetchKyberQuote(
                        srcToken,
                        dstToken,
                        srcTokenValue,
                        tokenValue,
                        vultBPSDiscount,
                        provider,
                        srcNativeToken,
                    )

                SwapProvider.ONEINCH ->
                    fetchOneInchQuote(
                        srcToken,
                        dstToken,
                        srcTokenValue,
                        tokenValue,
                        vultBPSDiscount,
                        provider,
                        srcNativeToken,
                    )

                SwapProvider.LIFI,
                SwapProvider.JUPITER ->
                    fetchLiFiJupiterQuote(
                        provider,
                        src,
                        dst,
                        srcToken,
                        dstToken,
                        srcTokenValue,
                        tokenValue,
                        vultBPSDiscount,
                        srcNativeToken,
                    )

                SwapProvider.SWAPKIT ->
                    fetchSwapKitQuote(
                        src,
                        dst,
                        srcToken,
                        dstToken,
                        srcTokenValue,
                        tokenValue,
                        vultBPSDiscount,
                        srcNativeToken,
                    )
            }

        val feeCoin =
            when (provider) {
                SwapProvider.MAYA,
                SwapProvider.THORCHAIN,
                SwapProvider.LIFI -> dstToken
                // SwapKit inbound fee is source-native (like 1inch/Kyber/Jupiter, not LiFi's
                // destination-side integrator model).
                SwapProvider.SWAPKIT -> srcNativeToken
                else -> srcNativeToken
            }

        val fiatFees = convertTokenValueToFiat(feeCoin, quote.fees, currency)
        val estimatedDstTokenValue = mapTokenValueToDecimalUiString(quote.expectedDstValue)

        // `expectedDstValue × dstMarketPrice` values the destination at an independent oracle
        // (e.g. CoinGecko), which for illiquid tokens diverges from the DEX pool rate the quote
        // actually executes at — inflating the destination fiat above the source (#4878). Clamp the
        // *displayed* destination fiat to the source fiat so it reflects the quoted rate (see
        // [clampDstFiatToSrcFiat]). The unclamped market value is still used for cross-provider
        // ranking below, where it scales with the destination amount and stays comparable across
        // providers.
        val marketDstFiatValue = convertTokenValueToFiat(dstToken, quote.expectedDstValue, currency)
        val estimatedDstFiatValue = clampDstFiatToSrcFiat(srcFiatValue, marketDstFiatValue)

        val rawFees =
            when (quote) {
                is SwapQuote.ThorChain -> quote.data.fees
                is SwapQuote.MayaChain -> quote.data.fees
                else -> null
            }
        val resolvedFeeText: String
        val outboundFeeText: String?
        val swapFeePercent: String?
        val resolvedSwapFeeFiat: FiatValue
        if (rawFees != null) {
            val affiliateFiat =
                convertTokenValueToFiat(
                    dstToken,
                    dstToken.convertToTokenValue(rawFees.affiliate),
                    currency,
                )
            val outboundFiat =
                convertTokenValueToFiat(
                    dstToken,
                    dstToken.convertToTokenValue(rawFees.outbound),
                    currency,
                )
            swapFeePercent =
                if (srcFiatValue.value > BigDecimal.ZERO)
                    String.format(
                        Locale.US,
                        "%.2f%%",
                        affiliateFiat.value
                            .divide(srcFiatValue.value, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal(100)),
                    )
                else null
            resolvedFeeText = fiatValueToString(affiliateFiat, asFee = true)
            outboundFeeText = fiatValueToString(outboundFiat, asFee = true)
            // Headline total must reconcile to the breakdown rows (Swap Fee + Outbound Fee).
            // Raw `fees.total` includes the `asset` (liquidity) component, but liquidity is
            // already reflected in `expectedDstValue`, so we drop it here.
            resolvedSwapFeeFiat =
                FiatValue(
                    value = affiliateFiat.value + outboundFiat.value,
                    currency = fiatFees.currency,
                )
        } else {
            resolvedFeeText = fiatValueToString(fiatFees, asFee = true)
            outboundFeeText = null
            swapFeePercent = null
            resolvedSwapFeeFiat = fiatFees
        }

        return QuoteFetchResult(
            quote = quote,
            provider = provider,
            providerUiText = providerText,
            srcFiatValueText = srcFiatValueText,
            estimatedDstTokenValue = estimatedDstTokenValue,
            estimatedDstFiatValue = fiatValueToString(estimatedDstFiatValue),
            estimatedDstFiat = marketDstFiatValue,
            feeText = resolvedFeeText,
            swapFeeFiat = resolvedSwapFeeFiat,
            outboundFeeText = outboundFeeText,
            swapFeePercent = swapFeePercent,
        )
    }

    internal suspend fun fetchBestQuote(
        candidates: List<QuoteCandidate>,
        src: SendSrc,
        dst: SendSrc,
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        currency: AppCurrency,
        amount: BigDecimal,
    ): BestQuote {
        if (candidates.isEmpty()) {
            throw SwapException.SwapIsNotSupported("Swap is not supported for this pair")
        }

        val results: List<Result<BestQuote>> = coroutineScope {
            candidates
                .map { candidate ->
                    async {
                        runCatching {
                                withTimeout(QUOTE_FETCH_TIMEOUT_MS) {
                                    BestQuote(
                                        candidate = candidate,
                                        result =
                                            fetchQuote(
                                                provider = candidate.provider,
                                                src = src,
                                                dst = dst,
                                                srcToken = srcToken,
                                                dstToken = dstToken,
                                                srcTokenValue = srcTokenValue,
                                                tokenValue = tokenValue,
                                                currency = currency,
                                                vultBPSDiscount = candidate.vultBPSDiscount,
                                                referral = candidate.referral,
                                                amount = amount,
                                            ),
                                    )
                                }
                            }
                            .onFailure { e ->
                                // TimeoutCancellationException extends CancellationException
                                // but is a transient per-provider failure — don't let it
                                // cancel sibling fetches via awaitAll.
                                if (
                                    e is CancellationException && e !is TimeoutCancellationException
                                )
                                    throw e
                                Timber.w(
                                    e,
                                    "Quote fetch failed provider=%s src=%s dst=%s amount=%s",
                                    candidate.provider,
                                    srcToken.id,
                                    dstToken.id,
                                    srcTokenValue,
                                )
                            }
                    }
                }
                .awaitAll()
        }

        val successes = results.mapNotNull { it.getOrNull() }
        if (successes.isEmpty()) {
            val failures = results.mapNotNull { it.exceptionOrNull() }
            // Surface the most actionable failure instead of the first by provider order
            // (iOS SwapService parity). When THORChain returns a generic "no route" error
            // while MAYA reports a recoverable amount/dust error for the same pair, the user
            // should see the amount error so they can adjust and retry. Ties keep provider
            // order (minBy returns the first match), so all-generic failures are unchanged.
            val selected = failures.minBy { swapFailurePriority(it) }
            // withTimeout surfaces a raw TimeoutCancellationException; map it into the typed
            // SwapException hierarchy so the form renders the localized timeout copy instead of
            // leaking a coroutine cancellation as the generic "quote failed" error.
            throw if (selected is TimeoutCancellationException)
                SwapException.TimeOut(selected.message ?: "Quote request timed out")
            else selected
        }

        // Rank on estimatedDstFiat alone — this represents the destination amount
        // the user expects to receive. Subtracting swapFeeFiat would double-count
        // for THOR/MAYA (their expectedAmountOut is already net of protocol fees)
        // and mix apples-to-oranges since swapFeeFiat is gas for 1inch/Kyber but
        // an integrator fee for LI.FI.
        //
        // On top of that metric a banded provider-preference layer applies: among
        // quotes within PROVIDER_PREFERENCE_BAND (1%) of the best output, the
        // highest-priority provider wins instead of the raw maximum. This keeps
        // near-tie routes on the more trusted/integrated provider without ever
        // trading away a materially better rate (anything outside the band loses
        // on output). iOS is the cross-platform anchor for this rule; the canonical
        // spec lives in vultisig-sdk and other platforms mirror this implementation.
        val best = successes.maxBy { it.result.estimatedDstFiat.value }
        val floor = best.result.estimatedDstFiat.value * (BigDecimal.ONE - PROVIDER_PREFERENCE_BAND)
        val inBand = successes.filter { it.result.estimatedDstFiat.value >= floor }
        return inBand.minWithOrNull(
            compareBy<BestQuote> { providerPriority(it.candidate.provider) }
                .thenByDescending { it.result.estimatedDstFiat.value }
        ) ?: best
    }

    /**
     * Fetches the best quote and maps any typed swap failure to a renderable
     * [QuoteResolution.Failure] so the ViewModel only has to surface it. Cancellation is rethrown
     * so the caller's `collectLatest` can still cancel an in-flight fetch.
     *
     * @param selectedSrcTokenTitle the source token's display title, used when formatting
     *   amount-too-low errors.
     */
    suspend fun resolveBestQuote(
        candidates: List<QuoteCandidate>,
        src: SendSrc,
        dst: SendSrc,
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        currency: AppCurrency,
        amount: BigDecimal,
        selectedSrcTokenTitle: String?,
    ): QuoteResolution =
        try {
            QuoteResolution.Success(
                fetchBestQuote(
                    candidates = candidates,
                    src = src,
                    dst = dst,
                    srcToken = srcToken,
                    dstToken = dstToken,
                    srcTokenValue = srcTokenValue,
                    tokenValue = tokenValue,
                    currency = currency,
                    amount = amount,
                )
            )
        } catch (e: SwapException) {
            QuoteResolution.Failure(
                formError = mapSwapExceptionToFormError(e, srcToken, selectedSrcTokenTitle),
                cause = e,
                tag = "swapError",
            )
        } catch (e: SwapKitError) {
            QuoteResolution.Failure(
                formError = mapSwapKitErrorToFormError(e),
                cause = e,
                tag = "swapKitError",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            QuoteResolution.Failure(
                formError = UiText.StringResource(R.string.swap_error_quote_failed),
                cause = e,
                tag = "swapUnexpectedError",
            )
        }

    /**
     * Provider preference order for the banded selection. Lower index = preferred. THORChain is
     * most preferred, then MayaChain, SwapKit, KyberSwap, 1inch, LI.FI; Jupiter (Solana-only,
     * rarely competing in the same candidate set) ranks last.
     */
    private fun providerPriority(provider: SwapProvider): Int =
        when (provider) {
            SwapProvider.THORCHAIN -> 0
            SwapProvider.MAYA -> 1
            SwapProvider.SWAPKIT -> 2
            SwapProvider.KYBER -> 3
            SwapProvider.ONEINCH -> 4
            SwapProvider.LIFI -> 5
            SwapProvider.JUPITER -> 6
        }

    /**
     * Ranks a failed-quote [error] so the most actionable failure is surfaced when every provider
     * fails. Lower values win. Amount-related errors mean the pair is routable and the user can
     * recover by adjusting the amount, so they rank above recoverable/transient errors, which in
     * turn rank above generic "no route" fallbacks.
     */
    private fun swapFailurePriority(error: Throwable): Int =
        when (error) {
            is SwapException.AmountBelowDustThreshold,
            is SwapException.SmallSwapAmount,
            is SwapException.InsufficentSwapAmount,
            is SwapException.AmountCannotBeZero,
            is SwapException.SameAssets -> 1
            is SwapException.InsufficientFunds,
            is SwapException.HighPriceImpact,
            is SwapException.RateLimitExceeded,
            is SwapException.TradingHalted,
            is SwapException.TimeOut,
            is TimeoutCancellationException,
            is SwapException.NetworkConnection,
            is SwapKitError.Network,
            is SwapKitError.InsufficientBalance,
            is SwapKitError.InsufficientAllowance -> 2
            is SwapException.SwapRouteNotAvailable,
            is SwapException.SwapIsNotSupported,
            is SwapException.UnkownSwapError,
            is SwapKitError.NoRoutes,
            is SwapKitError.SwapRouteNotFound,
            is SwapKitError.RouteFiltered,
            is SwapKitError.ProviderNotEnabled -> 3
            else -> 4
        }

    private suspend fun fetchThorMayaQuote(
        provider: SwapProvider,
        src: SendSrc,
        dst: SendSrc,
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        vultBPSDiscount: Int?,
        referral: String?,
        amount: BigDecimal,
    ): Pair<SwapQuote, UiText> {
        val isAffiliate = true
        val (quote, recommendedMinAmountToken) =
            if (provider == SwapProvider.MAYA) {
                val mayaSwapQuote =
                    getCachedQuoteOrFetch(
                        srcToken.id,
                        dstToken.id,
                        srcToken.address,
                        dstToken.address,
                        srcTokenValue,
                        SwapProvider.MAYA,
                    ) {
                        swapQuoteRepository
                            .getQuote(
                                SwapProvider.MAYA,
                                SwapQuoteRequest(
                                    srcToken = srcToken,
                                    dstToken = dstToken,
                                    tokenValue = tokenValue,
                                    dstAddress = dst.address.address,
                                    isAffiliate = isAffiliate,
                                    bpsDiscount = vultBPSDiscount ?: 0,
                                    referralCode = referral.orEmpty(),
                                ),
                            )
                            .expectNative(SwapProvider.MAYA)
                    }
                        as SwapQuote.MayaChain
                mayaSwapQuote to mayaSwapQuote.recommendedMinTokenValue
            } else {
                val thorSwapQuote =
                    getCachedQuoteOrFetch(
                        srcToken.id,
                        dstToken.id,
                        srcToken.address,
                        dstToken.address,
                        srcTokenValue,
                        SwapProvider.THORCHAIN,
                    ) {
                        swapQuoteRepository
                            .getQuote(
                                SwapProvider.THORCHAIN,
                                SwapQuoteRequest(
                                    srcToken = srcToken,
                                    dstToken = dstToken,
                                    tokenValue = tokenValue,
                                    dstAddress = dst.address.address,
                                    referralCode = referral.orEmpty(),
                                    bpsDiscount = vultBPSDiscount ?: 0,
                                ),
                            )
                            .expectNative(SwapProvider.THORCHAIN)
                    }
                        as SwapQuote.ThorChain
                thorSwapQuote to thorSwapQuote.recommendedMinTokenValue
            }

        val recommendedMinAmountTokenString =
            mapTokenValueToDecimalUiString(recommendedMinAmountToken)
        if (amount < recommendedMinAmountToken.decimal) {
            throw SwapException.SmallSwapAmount(recommendedMinAmountTokenString)
        }

        val providerText =
            if (provider == SwapProvider.MAYA) R.string.swap_form_provider_mayachain.asUiText()
            else R.string.swap_form_provider_thorchain.asUiText()

        return quote to providerText
    }

    private suspend fun fetchKyberQuote(
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        vultBPSDiscount: Int?,
        provider: SwapProvider,
        srcNativeToken: Coin,
    ): Pair<SwapQuote, UiText> {
        val swapQuote =
            getCachedQuoteOrFetch(
                srcToken.id,
                dstToken.id,
                srcToken.address,
                dstToken.address,
                srcTokenValue,
                SwapProvider.KYBER,
            ) {
                val apiQuote =
                    swapQuoteRepository
                        .getQuote(
                            SwapProvider.KYBER,
                            SwapQuoteRequest(
                                srcToken = srcToken,
                                dstToken = dstToken,
                                tokenValue = tokenValue,
                                affiliateBps =
                                    maxOf(0, KYBER_AFFILIATE_FEE_BPS - (vultBPSDiscount ?: 0)),
                            ),
                        )
                        .expectEvm(SwapProvider.KYBER)
                val expectedDstValue =
                    TokenValue(value = apiQuote.dstAmount.toBigInteger(), token = dstToken)
                val gasFees =
                    apiQuote.tx.gasPrice.toBigInteger() *
                        (apiQuote.tx.gas.takeIf { it != 0L } ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT)
                            .toBigInteger()
                val (feeAmount, feeCoin) =
                    resolveSwapFee(
                        apiQuote.tx.swapFeeTokenContract,
                        apiQuote.tx.swapFee,
                        srcNativeToken,
                        gasFees,
                    )
                val updatedTx = apiQuote.tx.withResolvedSwapFee(feeAmount, feeCoin)
                val tokenFees = TokenValue(value = feeAmount, token = feeCoin)
                SwapQuote.OneInch(
                    expectedDstValue = expectedDstValue,
                    fees = tokenFees,
                    data = apiQuote.copy(tx = updatedTx),
                    expiredAt = Clock.System.now() + expiredAfter,
                    provider = provider.getSwapProviderId(),
                )
            }
        return swapQuote to R.string.swap_for_provider_kyber.asUiText()
    }

    private suspend fun fetchOneInchQuote(
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        vultBPSDiscount: Int?,
        provider: SwapProvider,
        srcNativeToken: Coin,
    ): Pair<SwapQuote, UiText> {
        val isAffiliate = true
        val swapQuote =
            getCachedQuoteOrFetch(
                srcToken.id,
                dstToken.id,
                srcToken.address,
                dstToken.address,
                srcTokenValue,
                SwapProvider.ONEINCH,
            ) {
                val apiQuote =
                    swapQuoteRepository
                        .getQuote(
                            SwapProvider.ONEINCH,
                            SwapQuoteRequest(
                                srcToken = srcToken,
                                dstToken = dstToken,
                                tokenValue = tokenValue,
                                isAffiliate = isAffiliate,
                                bpsDiscount = vultBPSDiscount ?: 0,
                            ),
                        )
                        .expectEvm(SwapProvider.ONEINCH)
                val expectedDstValue =
                    TokenValue(value = apiQuote.dstAmount.toBigInteger(), token = dstToken)
                val tokenFees =
                    TokenValue(
                        value =
                            apiQuote.tx.gasPrice.toBigInteger() *
                                (apiQuote.tx.gas.takeIf { it != 0L }
                                        ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT)
                                    .toBigInteger(),
                        token = srcNativeToken,
                    )
                SwapQuote.OneInch(
                    expectedDstValue = expectedDstValue,
                    fees = tokenFees,
                    data = apiQuote,
                    expiredAt = Clock.System.now() + expiredAfter,
                    provider = provider.getSwapProviderId(),
                )
            }
        return swapQuote to R.string.swap_for_provider_1inch.asUiText()
    }

    private suspend fun fetchLiFiJupiterQuote(
        provider: SwapProvider,
        src: SendSrc,
        dst: SendSrc,
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        vultBPSDiscount: Int?,
        srcNativeToken: Coin,
    ): Pair<SwapQuote, UiText> {
        val swapQuote =
            getCachedQuoteOrFetch(
                srcToken.id,
                dstToken.id,
                srcToken.address,
                dstToken.address,
                srcTokenValue,
                provider,
            ) {
                val apiQuote =
                    if (provider == SwapProvider.LIFI)
                        swapQuoteRepository
                            .getQuote(
                                SwapProvider.LIFI,
                                SwapQuoteRequest(
                                    srcToken = srcToken,
                                    dstToken = dstToken,
                                    tokenValue = tokenValue,
                                    srcAddress = src.address.address,
                                    dstAddress = dst.address.address,
                                    bpsDiscount = vultBPSDiscount ?: 0,
                                ),
                            )
                            .expectEvm(SwapProvider.LIFI)
                    else
                        swapQuoteRepository
                            .getQuote(
                                SwapProvider.JUPITER,
                                SwapQuoteRequest(
                                    srcToken = srcToken,
                                    dstToken = dstToken,
                                    tokenValue = tokenValue,
                                    srcAddress = src.address.address,
                                ),
                            )
                            .expectEvm(SwapProvider.JUPITER)
                val expectedDstValue =
                    TokenValue(value = apiQuote.dstAmount.toBigInteger(), token = dstToken)
                val (feeAmount, feeCoin) =
                    if (provider == SwapProvider.LIFI) {
                        val feeWei =
                            LiFiChainApi.integratorFeeAmount(
                                dstAmount = apiQuote.dstAmount.toBigInteger(),
                                bpsDiscount = vultBPSDiscount ?: 0,
                            )
                        Pair(feeWei, dstToken)
                    } else {
                        resolveSwapFee(
                            apiQuote.tx.swapFeeTokenContract,
                            apiQuote.tx.swapFee,
                            srcNativeToken,
                            apiQuote.tx.swapFee.toBigInteger(),
                        )
                    }
                val updatedTx = apiQuote.tx.withResolvedSwapFee(feeAmount, feeCoin)
                val tokenFees = TokenValue(value = feeAmount, token = feeCoin)
                SwapQuote.OneInch(
                    expectedDstValue = expectedDstValue,
                    fees = tokenFees,
                    data = apiQuote.copy(tx = updatedTx),
                    expiredAt = Clock.System.now() + expiredAfter,
                    provider = provider.getSwapProviderId(),
                )
            }
        val providerText =
            if (provider == SwapProvider.LIFI) {
                R.string.swap_for_provider_li_fi.asUiText()
            } else {
                R.string.swap_for_provider_jupiter.asUiText()
            }
        return swapQuote to providerText
    }

    private suspend fun fetchSwapKitQuote(
        src: SendSrc,
        dst: SendSrc,
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        vultBPSDiscount: Int?,
        srcNativeToken: Coin,
    ): Pair<SwapQuote, UiText> {
        // iOS' SwapKit tier-discount formula at this milestone:
        //   max(0, min(1000, 50 - vultTierDiscount))
        // 50 bps base affiliate, clamped to 0..1000 (SwapKit's documented 0..10% range).
        val affiliateBps = (SWAPKIT_AFFILIATE_FEE_BPS - (vultBPSDiscount ?: 0)).coerceIn(0, 1000)
        val swapQuote =
            getCachedQuoteOrFetch(
                srcToken.id,
                dstToken.id,
                srcToken.address,
                dstToken.address,
                srcTokenValue,
                SwapProvider.SWAPKIT,
            ) {
                val result =
                    swapQuoteRepository.getQuote(
                        SwapProvider.SWAPKIT,
                        SwapQuoteRequest(
                            srcToken = srcToken,
                            dstToken = dstToken,
                            tokenValue = tokenValue,
                            srcAddress = src.address.address,
                            dstAddress = dst.address.address,
                            affiliateBps = affiliateBps,
                        ),
                    )
                val evmResult =
                    when (result) {
                        // Non-EVM SwapKit (Phase 2: BTC/PSBT) arrives as a fully-formed
                        // SwapQuote.SwapKit on the Native result — cache it directly.
                        is SwapQuoteResult.Native -> return@getCachedQuoteOrFetch result.quote
                        is SwapQuoteResult.Evm -> result
                    }
                val apiQuote = evmResult.data
                val expectedDstValue =
                    TokenValue(
                        value =
                            // SwapKitQuoteSource already scaled dstAmount to a raw-units integer
                            // string, so this parses cleanly today. Stay inside the typed error
                            // hierarchy on the off chance it ever doesn't: a raw `error()` throws
                            // IllegalStateException, which the form catches as the generic "quote
                            // failed" copy and leaks the raw string. Decoding lets the picker fall
                            // back to another provider and shows the localized decoding message.
                            apiQuote.dstAmount.toBigIntegerOrNull()
                                ?: throw SwapKitError.Decoding(
                                    "Malformed SwapKit dstAmount (raw units expected): ${apiQuote.dstAmount}"
                                ),
                        token = dstToken,
                    )
                // The source layer stages the inbound fee on tx.swapFee with an empty token
                // contract; resolveSwapFee's empty-contract branch returns the fallback. Pass the
                // parsed swapFee as fallback so an empty contract doesn't discard the fee and
                // render $0 Network Fee.
                val swapKitInboundFee = apiQuote.tx.swapFee.toBigIntegerOrNull() ?: BigInteger.ZERO
                val (feeAmount, feeCoin) =
                    resolveSwapFee(
                        apiQuote.tx.swapFeeTokenContract,
                        apiQuote.tx.swapFee,
                        srcNativeToken,
                        swapKitInboundFee,
                    )
                val tokenFees = TokenValue(value = feeAmount, token = feeCoin)
                SwapQuote.OneInch(
                    expectedDstValue = expectedDstValue,
                    fees = tokenFees,
                    data = apiQuote,
                    expiredAt = Clock.System.now() + expiredAfter,
                    // `provider` is the proto-serialized discriminator used by
                    // SwapTransactionToUiModelMapper to map back onto SwapProvider.SWAPKIT —
                    // keep it as the canonical id. The sub-provider drives the UI label below.
                    provider = SwapProvider.SWAPKIT.getSwapProviderId(),
                    subProvider = evmResult.subProvider,
                    // Correlation key for `/track` settlement gating of this cross-chain swap.
                    swapId = evmResult.swapId,
                )
            }
        // Read the sub-provider off the returned quote (not a hoisted var): a cache HIT skips the
        // fetch lambda, so a transient var would be null and the label would silently collapse from
        // "via CHAINFLIP" back to the generic "SwapKit" when the form re-opens within the TTL.
        val resolvedSubProvider =
            when (swapQuote) {
                is SwapQuote.OneInch -> swapQuote.subProvider
                is SwapQuote.SwapKit -> swapQuote.subProvider
                else -> null
            }
        val providerLabel =
            if (resolvedSubProvider.isNullOrBlank()) R.string.swap_for_provider_swapkit.asUiText()
            else formatSwapKitProviderLabel(resolvedSubProvider).asUiText()
        return swapQuote to providerLabel
    }

    // Stamps the resolved swap fee (`feeAmount`, already denominated in `feeCoin`) back onto the tx
    // together with the coin context so a join-flow co-signer — which has no live quote — renders
    // the same fiat value. Keeping the amount and the coin context in lockstep is what makes the
    // commondata swap_fee_chain/token_id/decimals fields trustworthy on the read side.
    private fun OneInchSwapTxJson.withResolvedSwapFee(feeAmount: BigInteger, feeCoin: Coin) =
        copy(
            swapFee = feeAmount.toString(),
            swapFeeChain = feeCoin.chain.id,
            swapFeeTokenContract = feeCoin.contractAddress,
            swapFeeDecimals = feeCoin.decimal,
        )

    private suspend fun resolveSwapFee(
        swapFeeTokenContract: String,
        swapFeeRaw: String,
        srcNativeToken: Coin,
        fallbackFee: BigInteger,
    ): Pair<BigInteger, Coin> =
        try {
            when {
                swapFeeTokenContract.isEmpty() -> Pair(fallbackFee, srcNativeToken)
                // Kyber / LI.FI / Jupiter use this sentinel for the native chain token —
                // the fee is already in src-native wei, no contract lookup needed.
                swapFeeTokenContract.equals(NATIVE_TOKEN_SENTINEL, ignoreCase = true) ->
                    Pair(swapFeeRaw.toBigIntegerOrNull() ?: fallbackFee, srcNativeToken)
                else -> {
                    val chainId = srcNativeToken.chain.id
                    val amount = swapFeeRaw.toBigInteger()
                    val coinAndFiatValue =
                        searchToken(chainId, swapFeeTokenContract)
                            ?: error("Can't find token or price")
                    val newNativeAmount =
                        convertTokenToTokenUseCase.convertTokenToToken(
                            amount,
                            coinAndFiatValue,
                            srcNativeToken,
                        )
                    Pair(newNativeAmount, srcNativeToken)
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.e(t)
            Pair(fallbackFee, srcNativeToken)
        }

    fun cacheQuote(
        quote: SwapQuote,
        provider: SwapProvider,
        srcTokenId: String,
        dstTokenId: String,
        srcAddress: String,
        dstAddress: String,
        srcAmount: BigInteger,
    ) {
        quoteCache.put(srcTokenId, dstTokenId, srcAddress, dstAddress, srcAmount, provider, quote)
    }

    private suspend fun getCachedQuoteOrFetch(
        srcTokenId: String,
        dstTokenId: String,
        srcAddress: String,
        dstAddress: String,
        srcAmount: BigInteger,
        provider: SwapProvider,
        fetch: suspend () -> SwapQuote,
    ): SwapQuote {
        quoteCache.get(srcTokenId, dstTokenId, srcAddress, dstAddress, srcAmount, provider)?.let {
            return it
        }
        return fetch().also { fresh ->
            quoteCache.put(
                srcTokenId,
                dstTokenId,
                srcAddress,
                dstAddress,
                srcAmount,
                provider,
                fresh,
            )
        }
    }

    fun mapSwapExceptionToFormError(
        e: SwapException,
        srcToken: Coin,
        selectedSrcTokenTitle: String?,
    ): UiText =
        when (e) {
            is SwapException.SwapIsNotSupported ->
                UiText.StringResource(R.string.swap_route_not_available)
            is SwapException.AmountCannotBeZero ->
                UiText.StringResource(R.string.swap_form_invalid_amount)
            is SwapException.SameAssets ->
                UiText.StringResource(R.string.swap_screen_same_asset_error_message)
            is SwapException.UnkownSwapError ->
                UiText.StringResource(R.string.swap_error_quote_failed)
            is SwapException.HighPriceImpact ->
                UiText.StringResource(R.string.swap_error_high_price_impact)
            is SwapException.InsufficentSwapAmount ->
                UiText.StringResource(R.string.swap_error_amount_too_low)
            is SwapException.SwapRouteNotAvailable ->
                UiText.StringResource(R.string.swap_route_not_available)
            is SwapException.TradingHalted ->
                UiText.StringResource(R.string.swap_error_trading_halted)
            is SwapException.TimeOut -> UiText.StringResource(R.string.swap_error_time_out)
            is SwapException.NetworkConnection ->
                UiText.StringResource(R.string.network_connection_lost)
            is SwapException.SmallSwapAmount -> {
                val rawAmount =
                    e.message?.let { msg ->
                        Regex("""recommended_min_amount_in:\s*(\d+)""")
                            .find(msg)
                            ?.groupValues
                            ?.get(1)
                            ?.toLongOrNull()
                    }
                if (rawAmount != null) {
                    val multiplier = srcToken.thorswapMultiplier
                    val tokenAmount =
                        BigDecimal(rawAmount)
                            .divide(multiplier)
                            .movePointRight(srcToken.decimal)
                            .toBigInteger()
                    val formattedAmount =
                        mapTokenValueToDecimalUiString(
                            TokenValue(value = tokenAmount, token = srcToken)
                        )
                    UiText.FormattedText(
                        R.string.swap_form_minimum_amount,
                        listOf(formattedAmount, selectedSrcTokenTitle ?: ""),
                    )
                } else if (e.message?.toDoubleOrNull() != null) {
                    UiText.FormattedText(
                        R.string.swap_form_minimum_amount,
                        listOf(e.message ?: "", selectedSrcTokenTitle ?: ""),
                    )
                } else {
                    e.message?.let { UiText.DynamicString(it) }
                        ?: UiText.StringResource(R.string.swap_error_amount_too_low)
                }
            }
            is SwapException.InsufficientFunds ->
                UiText.StringResource(R.string.swap_error_small_insufficient_funds)
            is SwapException.RateLimitExceeded ->
                UiText.StringResource(R.string.swap_error_rate_limit)
            is SwapException.AmountBelowDustThreshold ->
                UiText.StringResource(R.string.swap_error_amount_below_dust_threshold)
        }

    /** Localized message for each [SwapKitError] variant — surfaced verbatim on the swap form. */
    fun mapSwapKitErrorToFormError(e: SwapKitError): UiText =
        when (e) {
            is SwapKitError.ApiKeyMissing ->
                UiText.StringResource(R.string.swapkit_error_api_key_missing)
            is SwapKitError.ApiKeyInvalid ->
                UiText.StringResource(R.string.swapkit_error_api_key_invalid)
            is SwapKitError.InsufficientBalance ->
                UiText.StringResource(R.string.swapkit_error_insufficient_balance)
            is SwapKitError.InsufficientAllowance ->
                UiText.StringResource(R.string.swapkit_error_insufficient_allowance)
            is SwapKitError.UnableToBuildTransaction ->
                UiText.StringResource(R.string.swapkit_error_unable_to_build_transaction)
            is SwapKitError.SwapRouteNotFound ->
                UiText.StringResource(R.string.swapkit_error_swap_route_not_found)
            is SwapKitError.QuoteDeviation ->
                unescapedPercentLiteral(R.string.swapkit_error_output_amount_deviation_too_high)
            is SwapKitError.NoRoutes ->
                UiText.StringResource(R.string.swapkit_error_no_routes_found)
            is SwapKitError.BlackListAsset ->
                UiText.StringResource(R.string.swapkit_error_black_list_asset)
            is SwapKitError.InvalidSourceAddress ->
                UiText.StringResource(R.string.swapkit_error_invalid_source_address)
            is SwapKitError.InvalidDestinationAddress ->
                UiText.StringResource(R.string.swapkit_error_invalid_destination_address)
            is SwapKitError.AddressScreening ->
                UiText.StringResource(R.string.swapkit_error_address_screening)
            is SwapKitError.UnsupportedTxType ->
                UiText.FormattedText(R.string.swapkit_error_unsupported_tx_type, listOf(e.txType))
            is SwapKitError.ProviderNotEnabled ->
                UiText.StringResource(R.string.swapkit_error_provider_not_enabled)
            is SwapKitError.RouteFiltered ->
                UiText.StringResource(R.string.swapkit_error_route_filtered)
            is SwapKitError.MalformedAmount ->
                if (e.raw.isBlank()) UiText.StringResource(R.string.swapkit_error_decoding)
                else UiText.FormattedText(R.string.swapkit_error_malformed_amount, listOf(e.raw))
            is SwapKitError.Network -> UiText.StringResource(R.string.swapkit_error_network)
            is SwapKitError.Decoding -> UiText.StringResource(R.string.swapkit_error_decoding)
            is SwapKitError.Server ->
                // A null httpStatus rendered as `0` reads like a real status; fall back to the
                // generic network copy. fromCode never emits null, but Server can be constructed
                // directly elsewhere.
                e.httpStatus?.let { status ->
                    UiText.FormattedText(R.string.swapkit_error_server, listOf(status))
                } ?: UiText.StringResource(R.string.swapkit_error_network)
        }

    /**
     * Route a `%%`-escaped string resource through [UiText.FormattedText] so `String.format`
     * collapses `5%%` back to a literal `5%`. A plain [UiText.StringResource] skips `String.format`
     * and would render `5%%` verbatim; the resource cannot be un-escaped because Android lint
     * rejects an unescaped `%` adjacent to a letter (e.g. Dutch `5% afgeweken` would be flagged as
     * an incomplete `%a` specifier). Named so the unescape intent survives a switch to
     * [UiText.StringResource] at the call site.
     */
    private fun unescapedPercentLiteral(resId: Int): UiText =
        UiText.FormattedText(resId, emptyList())

    companion object {
        private const val KYBER_AFFILIATE_FEE_BPS = 50
        /** Base bps before tier discount; clamped to SwapKit's documented 0..1000 range. */
        private const val SWAPKIT_AFFILIATE_FEE_BPS = 50
        private const val NATIVE_TOKEN_SENTINEL = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        private const val QUOTE_FETCH_TIMEOUT_MS = 15_000L
        /**
         * Coalesces rapid free typing into a single quote fetch; bypassed (0ms) for percentage /
         * Max / paste via [quoteDebounceMillis].
         */
        private const val QUOTE_DEBOUNCE_MS = 300L
        /**
         * Matches the firm quote's UI formatter cap so the indicative value never out-renders it.
         */
        private const val MAX_INDICATIVE_DECIMALS = 8
        /**
         * Width of the priority band, as a fraction of the best output. Quotes whose output lands
         * within this band of the best are treated as effectively tied on rate, so the
         * higher-priority provider is preferred over a marginally larger raw output. 1%.
         */
        private val PROVIDER_PREFERENCE_BAND = BigDecimal("0.01")
    }
}

internal class QuoteCache(private val maxSize: Int = MAX_SIZE) {

    private data class Key(
        val srcTokenId: String,
        val dstTokenId: String,
        // The cached tx is address-bound (ERC-20 `tx.data`, SwapKit routing, destination). Two
        // vaults sharing a pair/amount have the same token ids but different account addresses, so
        // without these a quote built for vault A could route vault B's proceeds back to A.
        val srcAddress: String,
        val dstAddress: String,
        val srcAmount: BigInteger,
        val provider: SwapProvider,
    )

    private val lock = Any()
    private val entries = linkedMapOf<Key, SwapQuote>()

    fun get(
        srcTokenId: String,
        dstTokenId: String,
        srcAddress: String,
        dstAddress: String,
        srcAmount: BigInteger,
        provider: SwapProvider,
    ): SwapQuote? =
        synchronized(lock) {
            val key = Key(srcTokenId, dstTokenId, srcAddress, dstAddress, srcAmount, provider)
            val quote = entries[key] ?: return null
            if (Clock.System.now() < quote.expiredAt) {
                quote
            } else {
                entries.remove(key)
                null
            }
        }

    fun put(
        srcTokenId: String,
        dstTokenId: String,
        srcAddress: String,
        dstAddress: String,
        srcAmount: BigInteger,
        provider: SwapProvider,
        quote: SwapQuote,
    ) =
        synchronized(lock) {
            entries[Key(srcTokenId, dstTokenId, srcAddress, dstAddress, srcAmount, provider)] =
                quote
            evict()
        }

    private fun evict() {
        val now = Clock.System.now()
        entries.entries.removeAll { now >= it.value.expiredAt }
        val iter = entries.entries.iterator()
        while (entries.size > maxSize && iter.hasNext()) {
            iter.next()
            iter.remove()
        }
    }

    companion object {
        private const val MAX_SIZE = 6
    }
}
