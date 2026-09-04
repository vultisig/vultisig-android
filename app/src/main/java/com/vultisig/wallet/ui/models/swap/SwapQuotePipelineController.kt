package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.getProviderLogo
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.utils.minus
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the swap gas / network-fee state and the quote pipeline wiring, writing results back into
 * the shared [uiState]. Extracted from `SwapFormViewModel` so the quote/fee flow lives in one
 * cohesive, independently testable unit; the ViewModel only builds it via [Factory], [start]s it,
 * and reads the resolved quote/fee values it exposes.
 *
 * The repos / calculator / dispatcher are Hilt-injected here; the cache-bearing [swapQuoteManager]
 * is passed in (assisted) as the ViewModel's own instance so its flip-quote cache and
 * immediate-fetch flag don't split across two instances.
 */
internal class SwapQuotePipelineController
@AssistedInject
constructor(
    private val swapGasCalculator: SwapGasCalculator,
    private val swapQuoteRepository: SwapQuoteRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val fiatValueToString: FiatValueToStringMapper,
    private val referralRepository: ReferralCodeSettingsRepository,
    private val getDiscountBpsUseCase: GetDiscountBpsUseCase,
    private val convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase,
    private val swapDiscountChecker: SwapDiscountChecker,
    private val swapValidator: SwapValidator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val swapQuoteManager: SwapQuoteManager,
    @Assisted private val uiState: MutableStateFlow<SwapFormUiModel>,
    @Assisted("selectedSrc") private val selectedSrc: StateFlow<SendSrc?>,
    @Assisted("selectedDst") private val selectedDst: StateFlow<SendSrc?>,
    @Assisted private val referralCode: MutableStateFlow<String?>,
    @Assisted private val slippageBps: StateFlow<Int?>,
    @Assisted private val externalRecipient: StateFlow<String?>,
    @Assisted private val srcAmountState: TextFieldState,
    @Assisted private val vaultId: () -> String?,
    @Assisted private val showError: (UiText) -> Unit,
) {

    /**
     * Builds a [SwapQuotePipelineController] for one swap form. The repos / calculator / dispatcher
     * are Hilt-injected; the ViewModel supplies its [scope], the shared [swapQuoteManager], and the
     * form-owned state flows / callbacks as assisted params.
     */
    @AssistedFactory
    interface Factory {
        fun create(
            scope: CoroutineScope,
            swapQuoteManager: SwapQuoteManager,
            uiState: MutableStateFlow<SwapFormUiModel>,
            @Assisted("selectedSrc") selectedSrc: StateFlow<SendSrc?>,
            @Assisted("selectedDst") selectedDst: StateFlow<SendSrc?>,
            referralCode: MutableStateFlow<String?>,
            slippageBps: StateFlow<Int?>,
            externalRecipient: StateFlow<String?>,
            srcAmountState: TextFieldState,
            vaultId: () -> String?,
            showError: (UiText) -> Unit,
        ): SwapQuotePipelineController
    }

    // Built here (not injected) so the pipeline shares this controller's exact collaborator
    // instances — notably the assisted, cache-bearing [swapQuoteManager], whose flip-quote cache
    // would otherwise split across two instances.
    private val swapQuotePipeline =
        SwapQuotePipeline(
            swapQuoteRepository = swapQuoteRepository,
            appCurrencyRepository = appCurrencyRepository,
            referralRepository = referralRepository,
            getDiscountBpsUseCase = getDiscountBpsUseCase,
            convertTokenAndValueToTokenValue = convertTokenAndValueToTokenValue,
            swapQuoteManager = swapQuoteManager,
            swapDiscountChecker = swapDiscountChecker,
            swapGasCalculator = swapGasCalculator,
            swapValidator = swapValidator,
            fiatValueToString = fiatValueToString,
        )

    /** Mutable swap-quote state and the quote-coupled swap fee, shared with the ViewModel. */
    val quoteState = QuoteStateHolder()

    val estimatedNetworkFeeTokenValue = MutableStateFlow<TokenValue?>(null)
    val gasFee = MutableStateFlow<TokenValue?>(null)
    val gasFeeChain = MutableStateFlow<Chain?>(null)
    val estimatedNetworkFeeFiatValue = MutableStateFlow<FiatValue?>(null)

    // The last gas-pass display estimate from calculateGas, for the chain in [gasFeeChain]. An
    // EVM-aggregator quote overwrites the display with a route-gas re-base; this is what
    // resolveNetworkFee restores when a non-aggregator (THOR/Maya) route becomes active on an EVM
    // source, so the fee matches what a fresh fetch with that winner shows.
    private var evmBaselineEstimate: EstimatedGasFee? = null

    private val refreshQuoteState = MutableStateFlow(0)

    private var refreshQuoteJob: Job? = null

    // The input and ranked candidate set of the last applied quote, kept so a Select-route pick can
    // rebuild and apply another already-fetched candidate without a network round-trip. Cleared on
    // every reset/supersede path — a pick must never apply a quote for stale input.
    private var routeContext: RouteContext? = null

    // The in-flight manual route pick. The quote pipeline's collectLatest serializes its own
    // applies, but a pick runs on an independent coroutine — so every pipeline-owned apply and
    // reset cancels this first, keeping exactly one writer of quote state at a time.
    private var selectRouteJob: Job? = null

    // Ticket taken by every writer of quote state — each pipeline apply, each manual pick, each
    // reset. Cancelling the pick job only protects one direction: a pipeline apply that suspended
    // in resolveNetworkFee resumes with no idea a pick has landed since, and would stamp its own
    // network fee and isSwapDisabled over the quote now on screen. Whoever wrote the quote last
    // owns its fee, so a writer resuming on a stale ticket drops its outcome.
    private var quoteApplyGeneration = 0

    private data class RouteContext(val input: QuoteInput, val ranked: List<BestQuote>)

    // Suppresses the quote-refresh timer while the form isn't the foreground screen. The form's
    // scope (= viewModelScope) stays alive on the back stack once the flow proceeds to
    // verify/keysign, so without this the expiry timer would keep re-firing quote fetches — even on
    // the "Transaction failed" screen (#5128).
    private var isPaused = false

    // Whether the currently selected source/destination pair has any eligible swap provider.
    // Resolved up front on every pair change (#4710) so an unroutable pair surfaces guidance the
    // moment it is selected and never reaches the quote pipeline (which would throw
    // SwapIsNotSupported only after the user typed an amount and waited out the debounce).
    private var isPairSupported = true

    // A same-token pair is "supported" (no "no route" guidance while mid-pick) but has no provider
    // and can never be quoted, so the loading gate keys off routability, not mere support (#5296).
    private var isPairRoutable = false

    private val pairNotSupportedError = UiText.StringResource(R.string.swap_route_not_available)

    private val srcAmount: BigDecimal?
        get() = srcAmountState.text.toString().toBigDecimalOrNull()

    /**
     * True when the live field currently forms a quotable request: a routable pair (a real
     * provider, so same-token is excluded) with a positive source amount. Shared by
     * [startLoadingIfQuotable] and the late-result guard in [applyQuoteResult] so both agree on
     * what "quotable" means, keeping zero/invalid amounts and unroutable pairs quiet on both the
     * loading and result paths (#5296).
     */
    private val isLiveInputQuotable: Boolean
        get() {
            val amount = srcAmount
            return isPairRoutable && amount != null && amount > BigDecimal.ZERO
        }

    private var isLoading: Boolean
        get() = uiState.value.isLoading
        set(value) {
            uiState.update { it.copy(isLoading = value) }
        }

    /** Launches the gas calculation, quote pipeline, and total-fee observers on [scope]. */
    fun start() {
        warmEligibilityCache()
        calculateGas()
        observeQuotePipeline()
        collectTotalFee()
    }

    /**
     * Pre-warm the live THORChain / MayaChain pool eligibility cache the moment the swap screen
     * opens, so the synchronous [SwapQuoteRepository.getEligibleProviders] reads in
     * [updatePairSupport] / [showIndicativeRate] see freshly fetched routes instead of falling back
     * to the static table for a newly-available pair. Best-effort: a failed fetch keeps the
     * last-good (static) set, so the form stays usable offline.
     */
    private fun warmEligibilityCache() {
        scope.safeLaunch(onError = { Timber.e(it, "warmEligibilityCache") }) {
            swapQuoteRepository.refreshSwapEligibility()
        }
    }

    private fun calculateGas() {
        scope.launch {
            selectedSrc
                .filterNotNull()
                .map { sendSrc ->
                    val vaultId = vaultId() ?: return@map null
                    swapGasCalculator.calculateGasFee(sendSrc, vaultId)
                }
                .filterNotNull()
                .catch { Timber.e(it) }
                .collect { result ->
                    val chain = result.chain
                    val previousChain = gasFeeChain.value
                    gasFee.value = result.gasFee
                    gasFeeChain.value = chain
                    // UTXO non-Cardano fees are displayed from computeUtxoPlanFeeResult in
                    // calculateFees(); only update the display for non-UTXO chains here so
                    // a slow gas fetch can't overwrite the plan fee with a dust estimate.
                    if (chain.standard != TokenStandard.UTXO || chain == Chain.Cardano) {
                        try {
                            // Kept as the restore point for non-aggregator EVM routes: an
                            // aggregator quote re-bases the displayed fee onto its route gas, and
                            // resolveNetworkFee restores this exact estimate when a THOR/Maya
                            // route becomes active again.
                            evmBaselineEstimate = result.estimated
                            estimatedNetworkFeeFiatValue.value = result.estimated.fiatValue
                            estimatedNetworkFeeTokenValue.value = result.estimated.tokenValue

                            uiState.update {
                                it.copy(
                                    feeBreakdown =
                                        it.feeBreakdown.copy(
                                            networkFee = result.estimated.formattedTokenValue,
                                            networkFeeFiat = result.estimated.formattedFiatValue,
                                        )
                                )
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Timber.e(e)
                            showError(
                                UiText.StringResource(
                                    R.string.swap_screen_invalid_gas_fee_calculation
                                )
                            )
                        }
                    } else if (previousChain != chain) {
                        // UTXO non-Cardano + chain transitioned (initial selection or token
                        // switch). Clear any stale fee from the previous chain immediately so
                        // selectSrcPercentage() doesn't subtract a cross-chain fee value
                        // (e.g. ETH wei subtracted from ZEC satoshis) before calculateFees()
                        // can compute the correct UTXO plan fee.
                        evmBaselineEstimate = null
                        estimatedNetworkFeeTokenValue.value = null
                        estimatedNetworkFeeFiatValue.value = null
                        uiState.update {
                            it.copy(
                                feeBreakdown =
                                    it.feeBreakdown.copy(networkFee = "", networkFeeFiat = "")
                            )
                        }
                        // The plan-fee block in calculateFees() may have already run
                        // with a stale or null gasFeeChain and skipped via its chain guard,
                        // leaving the form fee blank; re-fire so it can compute with the byte
                        // fee for this chain.
                        refreshQuoteState.value++
                    }
                }
        }
    }

    private fun collectTotalFee() {
        estimatedNetworkFeeFiatValue
            .filterNotNull()
            .combine(quoteState.swapFeeFiat.filterNotNull()) { gasFeeFiat, swapFeeFiat ->
                gasFeeFiat + swapFeeFiat
            }
            .onEach { totalFee ->
                uiState.update {
                    it.copy(
                        feeBreakdown =
                            it.feeBreakdown.copy(
                                totalFee = fiatValueToString(totalFee, asFee = true)
                            )
                    )
                }
            }
            .launchIn(scope)
    }

    /**
     * Wires the source-amount / token-selection flows to the quote pipeline: shows the loading
     * spinner and an indicative rate on real user intent, debounces, then hands each input to
     * [SwapQuotePipeline] and applies its result (quote display, discounts, fees, refresh timer).
     * Named for the whole pipeline it drives — fees are only one part of what it produces.
     */
    @OptIn(FlowPreview::class)
    private fun observeQuotePipeline() {
        scope.safeLaunch {
            // Emits once per source-amount change carrying whether the quote should fetch
            // immediately (percentage / Max / paste) instead of waiting out the typing debounce.
            // Empty input still flows through so collectLatest hits the zero-amount branch and
            // resetQuoteState() clears the stale quote (#4712).
            val amountChanges = swapQuoteManager.amountChanges(srcAmountState.textAsFlow())

            combine(selectedSrc.filterNotNull(), selectedDst.filterNotNull()) { src, dst ->
                    src to dst
                }
                .distinctUntilChanged()
                // Re-emit the current pair once the warm-up pool fetch first populates, so a pair
                // picked before it landed (e.g. CACAO → ETH.USDT) is re-evaluated against the live
                // routes instead of latching "no route" from the cold static-only snapshot (#4975).
                .combine(swapQuoteRepository.swapEligibilityVersion) { pair, _ -> pair }
                .onEach { (src, dst) ->
                    // A freshly selected pair has no quote yet, and a token switch never clears the
                    // previous pair's destination value. Reset it so the skeleton shows while the
                    // new quote loads instead of the stale amount reading as a firm quote for the
                    // new pair (#4712 review).
                    uiState.update {
                        it.copy(
                            quoteDisplay =
                                it.quoteDisplay.copy(
                                    estimatedDstTokenValue = "0",
                                    isDstEstimated = false,
                                )
                        )
                    }
                    updatePairSupport(src, dst)
                }
                .combine(amountChanges) { address, immediate ->
                    QuoteInput(
                        address = address,
                        amount = srcAmount,
                        slippageBps = slippageBps.value,
                        externalRecipient = externalRecipient.value,
                        immediate = immediate,
                    )
                }
                // Fires on real user intent (typing, paste, percentage, token change) but not on
                // the
                // silent refreshes combined in below — so the spinner appears immediately ahead of
                // the debounce and an instant indicative estimate fills the destination field while
                // we wait, without flashing on background refreshes (#4712).
                .onEach { input ->
                    // Unroutable pair: the "no route" guidance already showed on selection (#4710),
                    // so don't spin or fetch an indicative estimate for a pair we can't quote.
                    if (!isPairSupported) return@onEach
                    startLoadingIfQuotable()
                    showIndicativeRate(input)
                }
                .combine(refreshQuoteState) { input, _ -> input }
                // A slippage or external-recipient change re-fetches with a different tolerance /
                // routing, so — when there is actually something to quote — raise isLoading to
                // disable the Swap button until the new quote lands, otherwise the prior,
                // differently-routed quote could be signed (#4858, review #4969). Routed through
                // startLoadingIfQuotable so an empty/zero amount stays quiet instead of blinking
                // the
                // skeletons (#5296). The onEach rides each flow individually, so the silent refresh
                // timer above doesn't flash the spinner.
                .combine(slippageBps.onEach { startLoadingIfQuotable() }) { input, liveSlippageBps
                    ->
                    input.copy(slippageBps = liveSlippageBps)
                }
                .combine(externalRecipient.onEach { startLoadingIfQuotable() }) {
                    input,
                    liveExternalRecipient ->
                    input.copy(externalRecipient = liveExternalRecipient)
                }
                // Percentage / Max / paste skip the debounce (0ms); free typing still coalesces at
                // 300ms so rapid edits fire a single quote fetch.
                .debounce { input -> swapQuoteManager.quoteDebounceMillis(input.immediate) }
                // collectLatest so newer input cancels an in-flight fetch instead of letting a
                // stale fetch write isLoading = false after the user has already typed again.
                .collectLatest { input ->
                    // Never request a quote for a pair with no eligible provider — that path throws
                    // SwapIsNotSupported. The guidance set on selection stands; just keep the
                    // spinner off and wait for the next pair change (#4710).
                    if (!isPairSupported) {
                        isLoading = false
                        return@collectLatest
                    }
                    when (
                        val result =
                            swapQuotePipeline.resolveQuote(
                                input = input,
                                // Read live, not from input.amount: the field may have been
                                // cleared during the debounce, and an empty field clears the quote
                                // silently rather than erroring (#4712).
                                isAmountFieldEmpty = srcAmountState.text.isEmpty(),
                                vaultId = vaultId(),
                                referralCode = referralCode.value,
                                currentDiscountInfo = uiState.value.discountInfo,
                                selectedSrcTokenTitle = uiState.value.selectedSrcToken?.title,
                                slippageBps = input.slippageBps,
                                externalRecipient = input.externalRecipient,
                            )
                    ) {
                        SwapQuotePipelineResult.Empty -> resetQuoteState()
                        is SwapQuotePipelineResult.Failure ->
                            resetQuoteState(
                                error = result.error,
                                cause = result.cause,
                                tag = result.tag,
                            )
                        is SwapQuotePipelineResult.Success -> applyQuoteResult(input, result)
                    }
                }
        }
    }

    /**
     * Writes a resolved quote into UI state, then runs the follow-up network-fee / balance pass and
     * arms the refresh timer. UTXO swaps stay disabled here until
     * [SwapQuotePipeline.resolveNetworkFee] verifies the plan fee, so a tap before then can never
     * submit with sats/byte as the total fee.
     */
    private suspend fun applyQuoteResult(
        input: QuoteInput,
        result: SwapQuotePipelineResult.Success,
        isManualRoutePick: Boolean = false,
    ) {
        // isAmountFieldEmpty is read once when resolveQuote is called, but the live input can
        // change
        // while this fetch — queued on a prior debounce cycle — is still in flight, and
        // collectLatest
        // can't cancel it until the next input clears the debounce. Re-check the live input here so
        // a
        // late-landing fetch for a now non-quotable field (cleared, zeroed, or an unroutable pair)
        // drops the stale quote instead of resurrecting it (#5296 review).
        //
        // isLiveInputQuotable only asks "is something quotable now"; it can't catch a fetch that
        // resolved for an earlier, still-positive amount on the same pair (type 5, pause past the
        // debounce, resume to 56 before 5's fetch lands). Also require the live amount to still
        // match the amount this fetch was resolved for, so a quote priced for the old amount can't
        // be applied — and then signed with a mismatched memo / slippage floor — over the new one
        // (#5310). Match the source/destination quote endpoints as well: changing to another
        // routable pair with the same amount creates the same pre-debounce landing window.
        // A pipeline-owned result supersedes any pick still applying: cancel it so a pick
        // suspended in resolveNetworkFee can't write its network fee / isSwapDisabled over the
        // quote this fresh result is about to apply.
        if (!isManualRoutePick) {
            selectRouteJob?.cancel()
            selectRouteJob = null
        }

        if (!isLiveInputQuotable) {
            // A stale pick must not clear the live request's state — the reset for the changed
            // input already ran (or will) on the pipeline path that owns it.
            if (isManualRoutePick) return
            resetQuoteState()
            return
        }

        val liveAmount = srcAmount
        val (inputSrc, inputDst) = input.address
        val isSuperseded =
            input.amount == null ||
                liveAmount == null ||
                liveAmount.compareTo(input.amount) != 0 ||
                !inputSrc.hasSameQuoteEndpointAs(selectedSrc.value) ||
                !inputDst.hasSameQuoteEndpointAs(selectedDst.value) ||
                input.slippageBps != slippageBps.value ||
                input.externalRecipient != externalRecipient.value
        if (isSuperseded) {
            // The discard path exists for pipeline-owned results, where this result IS the stale
            // state on screen. A pick's input was captured at fetch time, so when it trips this
            // guard the state on screen belongs to a NEWER request — dropping the pick silently is
            // the only safe move; discarding would strip the newer quote with no error, no spinner
            // and no armed refresh.
            if (isManualRoutePick) return
            discardSupersededQuoteResult()
            return
        }

        val (src, dst) = input.address

        val generation = ++quoteApplyGeneration
        routeContext = RouteContext(input, result.rankedQuotes)
        val routeOptions =
            buildRouteOptions(
                ranked = result.rankedQuotes,
                activeProvider = result.provider,
                srcToken = src.account.token,
                dstTicker = dst.account.token.ticker,
            )
        // buildRouteOptions suspends too (fiatValueToString reads the currency from DataStore), so
        // a writer can be overtaken before the display write below just like across
        // resolveNetworkFee: without this check it would land its quote/display over the newer
        // writer's and only its fee would be dropped by the later ticket check.
        if (generation != quoteApplyGeneration) return

        quoteState.provider = result.provider
        quoteState.quote = result.quote
        // Only the EVM-aggregator route consumes the gas-limit override at build time.
        quoteState.honorsGasLimitOverride.value = result.quote is SwapQuote.OneInch
        result.referralCodeToStore?.let { rc -> referralCode.update { rc } }
        quoteState.swapFeeFiat.value = result.swapFeeFiat

        uiState.update {
            it.copy(
                srcFiatValue = result.srcFiatValue,
                quoteDisplay =
                    it.quoteDisplay.copy(
                        provider = result.providerUiText,
                        estimatedDstTokenValue = result.estimatedDstTokenValue,
                        estimatedDstFiatValue = result.estimatedDstFiatValue,
                        isDstEstimated = false,
                        hasQuote = true,
                        expiredAt = result.expiredAt,
                    ),
                feeBreakdown =
                    it.feeBreakdown.copy(
                        fee = result.feeText,
                        outboundFee = result.outboundFeeText,
                        swapFeePercent = result.swapFeePercent,
                        swapFeeIncludedInRate = result.swapFeeIncludedInRate,
                        priceImpactPercent = result.priceImpactPercent,
                        priceImpactLevel = result.priceImpactLevel,
                    ),
                discountInfo = result.discountInfo,
                formError = null,
                isSwapDisabled = result.isUtxoSwap,
                isLoading = false,
                routeOptions = routeOptions,
                isRouteManuallySelected = isManualRoutePick,
            )
        }

        val networkFeeOutcome =
            swapQuotePipeline.resolveNetworkFee(
                result = result,
                src = src,
                vaultId = vaultId(),
                gasFee = gasFee.value,
                gasFeeChain = gasFeeChain.value,
                networkFeeTokenValue = estimatedNetworkFeeTokenValue.value,
                evmBaselineEstimate = evmBaselineEstimate,
            )
        // resolveNetworkFee suspends (plan fetch, balance read). Another writer — a manual pick, a
        // fresh pipeline result, a reset — may own the quote on screen by now, and it arms its own
        // fee and timer; this outcome belongs to a quote that is no longer displayed.
        if (generation != quoteApplyGeneration) return
        applyNetworkFeeOutcome(networkFeeOutcome)

        quoteState.quote?.expiredAt?.let { launchRefreshQuoteTimer(it) }
    }

    /**
     * Maps the ranked candidate set onto Select-route picker rows: the active route pinned to the
     * top, the rest keeping their best→worst net-output order. Fewer than two routes means there is
     * nothing to pick, so the list stays empty and the Select route row disables.
     */
    private suspend fun buildRouteOptions(
        ranked: List<BestQuote>,
        activeProvider: SwapProvider,
        srcToken: Coin,
        dstTicker: String,
    ): List<SwapRouteUiModel> {
        if (ranked.size < 2) return emptyList()
        return ranked
            .map { candidate ->
                val fetch = candidate.result
                // Only THORChain/Maya expose a completion estimate; aggregator rows render the fee
                // alone.
                val etaSeconds =
                    when (val quote = fetch.quote) {
                        is SwapQuote.ThorChain -> quote.data.totalSwapSeconds
                        is SwapQuote.MayaChain -> quote.data.totalSwapSeconds
                        else -> null
                    }
                // Mirrors buildSuccess's fee treatment so a row never advertises a fee that
                // vanishes from the breakdown the moment the route is picked: SwapKit UTXO-family
                // deposits surface their cost once as the Network Fee (the wire inbound fee would
                // double-count it), and 1inch bakes the affiliate fee into the quoted rate — in
                // both cases there is no separate amount to show, so the segment is dropped.
                val hidesSwapFee =
                    fetch.swapFeeIncludedInRate ||
                        (fetch.quote is SwapQuote.SwapKit &&
                            srcToken.chain.standard == TokenStandard.UTXO)
                // Grossed to the list rate exactly as the breakdown this row opens does, so a route
                // can't advertise one fee in the picker and a larger one a tap later. Whatever the
                // breakdown adds back onto the charged fee is added here too, off the same source
                // snapshot — the row carries no rate of its own, so it passes no [listRate].
                val pickerFee =
                    swapFeeRow(
                            provider = candidate.candidate.provider,
                            netFee = fetch.swapFeeFiat,
                            listRate = null,
                            srcFiat = fetch.srcFiat,
                            discounts = candidate.candidate.discountBps(),
                            feeIncludedInRate = fetch.swapFeeIncludedInRate,
                        )
                        .fee
                SwapRouteUiModel(
                    provider = candidate.candidate.provider,
                    name = fetch.providerUiText,
                    logo = getProviderLogo(candidate.candidate.provider.getSwapProviderId()),
                    feeText =
                        if (hidesSwapFee) null else fiatValueToString(pickerFee, asFee = true),
                    etaText =
                        etaSeconds?.let {
                            UiText.FormattedText(R.string.swap_route_eta, listOf(it))
                        },
                    // Formatted from the raw quote amount, NOT estimatedDstTokenValue: the display
                    // formatter abbreviates from 1e6 with a single decimal ("~1.1M"), which
                    // collapses the differences this column exists to compare.
                    outputText =
                        "~${formatRouteOutput(fetch.quote.expectedDstValue.decimal)} $dstTicker",
                    outputFiatText = fetch.estimatedDstFiatValue,
                    isSelected = candidate.candidate.provider == activeProvider,
                )
            }
            // Stable sort: pins the active route to the top, the rest keep their output ranking.
            .sortedByDescending { it.isSelected }
    }

    /**
     * Applies a manual route pick from the Select-route sheet: rebuilds the display state for the
     * picked, already-fetched candidate and applies it through the same path as an auto-resolved
     * quote, so fees, discounts, balance validation, and the refresh timer all follow the pick. No
     * network fetch — the pick only chooses among the quotes of the last resolution, and the next
     * refresh re-defaults the route to the automatic best (iOS parity).
     */
    fun selectRoute(provider: SwapProvider) {
        val ctx = routeContext ?: return
        if (quoteState.provider == provider) return
        val candidate = ctx.ranked.firstOrNull { it.candidate.provider == provider } ?: return
        // A row that lapsed while the sheet was open can no longer be signed at its quoted rate:
        // don't apply it (Swap would stay enabled against an expired quote) — refresh instead, so
        // a fresh candidate set replaces the whole list.
        if (Instant.now() >= candidate.result.quote.expiredAt) {
            refreshQuoteState.value++
            return
        }
        selectRouteJob?.cancel()
        selectRouteJob =
            scope.safeLaunch(onError = { Timber.e(it, "selectRoute") }) {
                val (src, _) = ctx.input.address
                val amount = ctx.input.amount ?: return@safeLaunch
                val srcToken = src.account.token
                val srcTokenValue = amount.movePointRight(srcToken.decimal).toBigInteger()
                val tokenValue = convertTokenAndValueToTokenValue(srcToken, srcTokenValue)
                val result =
                    swapQuotePipeline.buildSuccess(
                        bestQuote = candidate,
                        src = src,
                        srcTokenValue = srcTokenValue,
                        tokenValue = tokenValue,
                        currentDiscountInfo = uiState.value.discountInfo,
                        rankedQuotes = ctx.ranked,
                    )
                // buildSuccess suspends (discount checks); a refresh or reset may have replaced or
                // cleared the context meanwhile. That newer state wins — drop the pick.
                if (routeContext !== ctx) return@safeLaunch
                // applyQuoteResult re-runs the superseded/quotable guards against the live input;
                // on the manual path a trip drops the pick silently instead of resetting state
                // owned by a newer request.
                applyQuoteResult(ctx.input, result, isManualRoutePick = true)
            }
    }

    /** Applies the UTXO plan-fee / balance outcome to the network-fee flows and form state. */
    private fun applyNetworkFeeOutcome(outcome: NetworkFeeOutcome) {
        when (val fee = outcome.networkFee) {
            is NetworkFeeUpdate.Set -> {
                estimatedNetworkFeeFiatValue.value = fee.fiatValue
                estimatedNetworkFeeTokenValue.value = fee.tokenValue
            }
            NetworkFeeUpdate.Clear -> {
                estimatedNetworkFeeTokenValue.value = null
                estimatedNetworkFeeFiatValue.value = null
            }
            null -> Unit
        }
        uiState.update {
            val feeBreakdown =
                when (val fee = outcome.networkFee) {
                    is NetworkFeeUpdate.Set ->
                        it.feeBreakdown.copy(
                            networkFee = fee.formattedTokenValue,
                            networkFeeFiat = fee.formattedFiatValue,
                        )
                    NetworkFeeUpdate.Clear ->
                        it.feeBreakdown.copy(networkFee = "", networkFeeFiat = "", totalFee = "")
                    null -> it.feeBreakdown
                }
            it.copy(
                feeBreakdown = feeBreakdown,
                isSwapDisabled = outcome.isSwapDisabled,
                formError = outcome.formError,
            )
        }
    }

    /**
     * Reconciles quote-driven UI state with the current input on a trigger (typing, pair, slippage,
     * or recipient change), ahead of the debounced fetch:
     * - Routable pair (a real provider, so same-token is excluded) with a positive source amount:
     *   raise the spinner so the destination/fee skeletons and disabled Swap button lead the fetch.
     * - Nothing to quote (empty/zero field or an unroutable pair): leave the spinner off so the
     *   skeletons never flash true→false (blink) on form open or a bare pair/slippage/recipient
     *   change (#4712, #5296). If a resolved quote is still on screen, clear it now so a cleared or
     *   zeroed amount disables Swap and drops the stale destination/fee immediately instead of
     *   leaving them tappable until the 300ms debounce runs resetQuoteState (#5296 review).
     */
    private fun startLoadingIfQuotable() {
        if (isLiveInputQuotable) {
            isLoading = true
        } else if (uiState.value.quoteDisplay.hasQuote || uiState.value.isLoading) {
            // Clear a resolved quote OR a spinner we raised while a firm quote was still pending:
            // clearing a quotable amount before its quote lands leaves hasQuote false, so gating
            // only on hasQuote would strand isLoading = true for the rest of the debounce (#5296
            // review).
            resetQuoteState()
        }
    }

    /**
     * Fill the destination field with an instant indicative estimate from cached spot prices while
     * the firm quote resolves, so it never blanks on input or while refetching (#4712). Cached-only
     * and display-only: a cold price leaves the previous value untouched, and the firm quote always
     * overwrites this with [SwapFormUiModel.isDstEstimated] = false.
     */
    private suspend fun showIndicativeRate(input: QuoteInput) {
        // This runs in an onEach upstream of (and outside) the collectLatest try/catch, so any
        // throw from the suspending price read would escape into safeLaunch and end the whole quote
        // collection while isLoading stays stuck true. Contain it here (#4712 review).
        try {
            val (src, dst) = input.address
            val srcToken = src.account.token
            val dstToken = dst.account.token
            val amount = input.amount ?: return
            if (amount <= BigDecimal.ZERO || srcToken == dstToken) return

            // Skip pairs we can't actually quote: showing an indicative estimate for an
            // unsupported pair only to wipe it back to "0" once the firm fetch fails flashes a
            // receivable amount, which is jumpier than a steady "0" (#4712 review).
            // getEligibleProviders is a local table lookup, so this stays instant.
            if (swapQuoteRepository.getEligibleProviders(srcToken, dstToken).isEmpty()) return

            val currency = appCurrencyRepository.currency.first()
            val indicative =
                swapQuoteManager.computeIndicativeQuote(srcToken, dstToken, amount, currency)
                    ?: return

            uiState.update {
                it.copy(
                    quoteDisplay =
                        it.quoteDisplay.copy(
                            estimatedDstTokenValue = indicative.estimatedDstTokenValue,
                            estimatedDstFiatValue = indicative.estimatedDstFiatValue,
                            isDstEstimated = true,
                        )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "showIndicativeRate")
        }
    }

    /**
     * Stops quote polling when the form leaves the foreground (navigating into verify/keysign or
     * the app backgrounding). Cancels the pending refresh timer and blocks it from being re-armed
     * by the per-quote scheduling in [applyQuoteResult] while paused (#5128).
     */
    fun pause() {
        isPaused = true
        refreshQuoteJob?.cancel()
        refreshQuoteJob = null
    }

    /**
     * Resumes quote polling when the form returns to the foreground, re-arming the refresh timer
     * from the current quote's expiry so a quote that expired while paused refetches promptly
     * (#5128).
     */
    fun resume() {
        isPaused = false
        quoteState.quote?.expiredAt?.let { launchRefreshQuoteTimer(it) }
    }

    private fun launchRefreshQuoteTimer(expiredAt: Instant) {
        refreshQuoteJob?.cancel()
        // Paused: the form is off-screen, so don't re-arm the timer that would keep re-fetching
        // quotes in the background (#5128).
        if (isPaused) {
            refreshQuoteJob = null
            return
        }
        refreshQuoteJob =
            scope.launch(ioDispatcher) {
                delay(expiredAt - Instant.now())
                refreshQuoteState.value++
            }
    }

    /**
     * Whether [srcToken] → [dstToken] can actually be quoted: a distinct pair with at least one
     * eligible provider. Same-token pairs are "supported" but never routable. Shared by the pair
     * gate here and the token-selection loading gate so both key off the same predicate as
     * [startLoadingIfQuotable]'s [isPairRoutable] flag, rather than a looser amount-only check
     * (#5296 review). [SwapQuoteRepository.getEligibleProviders] is a local table lookup, so this
     * is instant and safe to call on the selection path.
     */
    fun isPairRoutable(srcToken: Coin, dstToken: Coin): Boolean =
        srcToken != dstToken &&
            swapQuoteRepository.getEligibleProviders(srcToken, dstToken).isNotEmpty()

    /**
     * Resolves whether the selected source/destination pair has any eligible provider and surfaces
     * the "no route" guidance immediately on selection, instead of letting the quote pipeline throw
     * SwapIsNotSupported only after the user has typed an amount and waited for a quote (#4710).
     *
     * Same-token pairs are treated as supported here — the zero-amount / same-asset guards handle
     * those — so we never flash "no route" while the user is mid-pick. Eligibility stays driven by
     * the static [com.vultisig.wallet.data.repositories.swap.SwapProviderTable] (a local lookup, so
     * this is instant); moving it to live provider/token data is deferred (#4685).
     */
    private fun updatePairSupport(src: SendSrc, dst: SendSrc) {
        val srcToken = src.account.token
        val dstToken = dst.account.token
        isPairRoutable = isPairRoutable(srcToken, dstToken)
        isPairSupported = srcToken == dstToken || isPairRoutable
        if (!isPairSupported) {
            resetQuoteState(error = pairNotSupportedError, cause = null, tag = null)
        } else if (uiState.value.formError == pairNotSupportedError) {
            // Moving from an unroutable pair to a routable one clears the stale guidance at once,
            // ahead of the debounced quote that would otherwise clear it ~300ms later.
            uiState.update { it.copy(formError = null) }
        }
    }

    /** Clears the quote, swap fee, and quote-derived form state without surfacing an error. */
    fun resetQuoteState() {
        resetQuoteState(error = null, cause = null, tag = null)
    }

    private fun SendSrc.hasSameQuoteEndpointAs(live: SendSrc?): Boolean =
        live != null &&
            account.token == live.account.token &&
            address.chain == live.address.chain &&
            address.address == live.address.address

    /**
     * Drops quote state owned by an older request without disturbing the newer quotable request's
     * spinner or indicative destination estimate. The newer request has already passed through
     * [startLoadingIfQuotable] / [showIndicativeRate] and is waiting in the debounce, so a full
     * [resetQuoteState] here would make that active request look idle until its fetch completes.
     */
    private fun discardSupersededQuoteResult() {
        refreshQuoteJob?.cancel()
        refreshQuoteJob = null
        // The context a pending pick was built from is going away with the quote it belonged to.
        selectRouteJob?.cancel()
        selectRouteJob = null
        // Clearing the quote invalidates any fee still resolving for it, the same way a newer
        // apply does — otherwise it resumes and writes a network fee onto state with no quote.
        quoteApplyGeneration++
        quoteState.reset()
        routeContext = null
        uiState.update {
            it.copy(
                srcFiatValue = "0",
                quoteDisplay =
                    it.quoteDisplay.copy(
                        provider = UiText.Empty,
                        hasQuote = false,
                        expiredAt = null,
                    ),
                routeOptions = emptyList(),
                isRouteManuallySelected = false,
                feeBreakdown =
                    it.feeBreakdown.copy(
                        fee = "0",
                        totalFee = "0",
                        outboundFee = null,
                        swapFeePercent = null,
                        swapFeeIncludedInRate = false,
                        priceImpactPercent = null,
                        priceImpactLevel = null,
                    ),
                discountInfo = DiscountInfo(),
                isSwapDisabled = true,
                formError = null,
            )
        }
    }

    private fun resetQuoteState(error: UiText?, cause: Throwable?, tag: String?) {
        // The prior quote's refresh timer would otherwise fire mid-flip/mid-error and re-run the
        // quote pipeline against the same invalid amount, briefly re-exposing the fee block.
        refreshQuoteJob?.cancel()
        refreshQuoteJob = null
        // The context a pending pick was built from is going away with the quote it belonged to.
        selectRouteJob?.cancel()
        selectRouteJob = null
        // Clearing the quote invalidates any fee still resolving for it, the same way a newer
        // apply does — otherwise it resumes and writes a network fee onto state with no quote.
        quoteApplyGeneration++
        // Clears quote/provider and the swap fee in one place. Resetting swapFeeFiat lets
        // collectTotalFee()'s filterNotNull() short-circuit so a later calculateGas() update can't
        // write a (newGas + staleSwap) combination back into state.totalFee — the same race that
        // triggers on flipSelectedTokens since selectedSrc changes synchronously.
        quoteState.reset()
        routeContext = null
        uiState.update {
            it.copy(
                srcFiatValue = "0",
                quoteDisplay = QuoteDisplay(),
                routeOptions = emptyList(),
                isRouteManuallySelected = false,
                feeBreakdown =
                    it.feeBreakdown.copy(
                        fee = "0",
                        totalFee = "0",
                        outboundFee = null,
                        swapFeePercent = null,
                        swapFeeIncludedInRate = false,
                        priceImpactPercent = null,
                        priceImpactLevel = null,
                    ),
                discountInfo = DiscountInfo(),
                isSwapDisabled = true,
                formError = error,
                isLoading = false,
            )
        }
        if (cause != null) {
            Timber.e(cause, tag)
        }
    }
}

// The thresholds iOS abbreviates a displayed amount at.
private val ROUTE_OUTPUT_MILLION: BigDecimal = BigDecimal.ONE.movePointRight(6)
private val ROUTE_OUTPUT_BILLION: BigDecimal = BigDecimal.ONE.movePointRight(9)
private val ROUTE_OUTPUT_TRILLION: BigDecimal = BigDecimal.ONE.movePointRight(12)

// Route-row output formatters, mirroring iOS `Decimal.formatForDisplay()`: grouped with up to eight
// fraction digits below 1e6, abbreviated to two decimals from there up. Full precision where the
// routes are actually compared digit by digit — the display mapper abbreviates from 1e6 with one
// decimal and collapses the very differences this column exists to show — but bounded above it,
// because the output Column carries no weight while the provider name carries weight(1f): an
// unabbreviated nine-figure amount measures wider than the row's whole two-column budget and
// squeezes the name to nothing. Truncating, never rounding up, so a row can't advertise more output
// than the quote pays.
// Main-confined like the controller that uses them (DecimalFormat is not thread-safe).
private val routeOutputFormat =
    DecimalFormat("#,##0.########").apply { roundingMode = RoundingMode.DOWN }

private val routeAbbreviatedOutputFormat =
    DecimalFormat("#,##0.##").apply { roundingMode = RoundingMode.DOWN }

/**
 * A Select-route row's output amount, formatted the way iOS formats its own (`formatForDisplay`):
 * full precision below a million, abbreviated to two decimals from there up so the column stays
 * inside the row.
 */
internal fun formatRouteOutput(amount: BigDecimal): String {
    val magnitude = amount.abs()
    return when {
        magnitude >= ROUTE_OUTPUT_TRILLION ->
            routeAbbreviatedOutputFormat.format(amount.movePointLeft(12)) + "T"
        magnitude >= ROUTE_OUTPUT_BILLION ->
            routeAbbreviatedOutputFormat.format(amount.movePointLeft(9)) + "B"
        magnitude >= ROUTE_OUTPUT_MILLION ->
            routeAbbreviatedOutputFormat.format(amount.movePointLeft(6)) + "M"
        else -> routeOutputFormat.format(amount)
    }
}
