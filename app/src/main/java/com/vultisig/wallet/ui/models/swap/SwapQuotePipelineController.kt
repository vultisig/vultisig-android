package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigDecimal
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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

    private val refreshQuoteState = MutableStateFlow(0)

    private var refreshQuoteJob: Job? = null

    // Whether the currently selected source/destination pair has any eligible swap provider.
    // Resolved up front on every pair change (#4710) so an unroutable pair surfaces guidance the
    // moment it is selected and never reaches the quote pipeline (which would throw
    // SwapIsNotSupported only after the user typed an amount and waited out the debounce).
    private var isPairSupported = true

    private val pairNotSupportedError = UiText.StringResource(R.string.swap_route_not_available)

    private val srcAmount: BigDecimal?
        get() = srcAmountState.text.toString().toBigDecimalOrNull()

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
        scope.launch {
            try {
                swapQuoteRepository.refreshSwapEligibility()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "warmEligibilityCache")
            }
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
                    QuoteInput(address = address, amount = srcAmount, immediate = immediate)
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
                    isLoading = true
                    showIndicativeRate(input)
                }
                .combine(refreshQuoteState) { input, _ -> input }
                // A slippage or external-recipient change re-fetches with a different tolerance /
                // routing, so raise isLoading to disable the Swap button until the new quote lands
                // —
                // otherwise the prior, differently-routed quote could be signed (#4858, review
                // #4969). The onEach rides each flow individually, so the silent refresh timer
                // above
                // doesn't flash the spinner.
                .combine(slippageBps.onEach { isLoading = true }) { input, _ -> input }
                .combine(externalRecipient.onEach { isLoading = true }) { input, _ -> input }
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
                                slippageBps = slippageBps.value,
                                externalRecipient = externalRecipient.value,
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
    ) {
        val (src, _) = input.address

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
                    ),
                discountInfo = result.discountInfo,
                formError = null,
                isSwapDisabled = result.isUtxoSwap,
                isLoading = false,
            )
        }

        applyNetworkFeeOutcome(
            swapQuotePipeline.resolveNetworkFee(
                result = result,
                src = src,
                vaultId = vaultId(),
                gasFee = gasFee.value,
                gasFeeChain = gasFeeChain.value,
                networkFeeTokenValue = estimatedNetworkFeeTokenValue.value,
            )
        )

        quoteState.quote?.expiredAt?.let { launchRefreshQuoteTimer(it) }
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

    private fun launchRefreshQuoteTimer(expiredAt: Instant) {
        refreshQuoteJob?.cancel()
        refreshQuoteJob =
            scope.launch(ioDispatcher) {
                delay(expiredAt - Clock.System.now())
                refreshQuoteState.value++
            }
    }

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
        isPairSupported =
            srcToken == dstToken ||
                swapQuoteRepository.getEligibleProviders(srcToken, dstToken).isNotEmpty()
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

    private fun resetQuoteState(error: UiText?, cause: Throwable?, tag: String?) {
        // The prior quote's refresh timer would otherwise fire mid-flip/mid-error and re-run the
        // quote pipeline against the same invalid amount, briefly re-exposing the fee block.
        refreshQuoteJob?.cancel()
        refreshQuoteJob = null
        // Clears quote/provider and the swap fee in one place. Resetting swapFeeFiat lets
        // collectTotalFee()'s filterNotNull() short-circuit so a later calculateGas() update can't
        // write a (newGas + staleSwap) combination back into state.totalFee — the same race that
        // triggers on flipSelectedTokens since selectedSrc changes synchronously.
        quoteState.reset()
        uiState.update {
            it.copy(
                srcFiatValue = "0",
                quoteDisplay = QuoteDisplay(),
                feeBreakdown =
                    it.feeBreakdown.copy(
                        fee = "0",
                        totalFee = "0",
                        outboundFee = null,
                        swapFeePercent = null,
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
