package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.usecases.getTierType
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timber.log.Timber

internal data class PreFlipState(
    val srcAmount: String,
    val srcTokenId: String,
    val dstTokenId: String,
    val flippedAmount: String,
)

/**
 * Mutable swap-quote state confined to the main thread.
 *
 * Every read/write happens from Main.immediate-dispatched code (the quote pipeline, the flip
 * gesture, and the reset paths), so these plain `var`s need no synchronization. Grouping them here
 * keeps that threading invariant explicit in one place instead of scattering it across fields.
 *
 * Shared between [SwapFormViewModel] (which reads [quote]/[provider] and owns [preFlipState] for
 * the flip gesture) and [SwapQuoteOrchestrator] (which writes [quote]/[provider] from the quote
 * pipeline).
 */
internal class QuoteStateHolder {
    var quote: SwapQuote? = null
    var provider: SwapProvider? = null
    var preFlipState: PreFlipState? = null
}

/**
 * Shared, ViewModel-owned state and read accessors the [SwapQuoteOrchestrator] pipeline both reads
 * and writes. Bundling them keeps the orchestrator's launch surface a single argument instead of a
 * dozen flows/lambdas, and keeps every write target pointing at the exact same [MutableStateFlow]
 * instances [SwapFormViewModel] exposes — so relocating the pipeline changes nothing about which
 * state it mutates or in what order.
 */
internal class SwapQuoteContext(
    val uiState: MutableStateFlow<SwapFormUiModel>,
    val quoteState: QuoteStateHolder,
    val swapFeeFiat: MutableStateFlow<FiatValue?>,
    val estimatedNetworkFeeTokenValue: MutableStateFlow<TokenValue?>,
    val estimatedNetworkFeeFiatValue: MutableStateFlow<FiatValue?>,
    val gasFee: MutableStateFlow<TokenValue?>,
    val gasFeeChain: MutableStateFlow<Chain?>,
    val refreshQuoteState: MutableStateFlow<Int>,
    val selectedSrc: MutableStateFlow<SendSrc?>,
    val selectedDst: MutableStateFlow<SendSrc?>,
    val srcAmountTextFlow: Flow<CharSequence>,
    val swapQuoteManager: SwapQuoteManager,
    val srcAmount: () -> BigDecimal?,
    val isSrcAmountEmpty: () -> Boolean,
    val vaultId: () -> String?,
    val selectedSrcTokenTitle: () -> String?,
)

/**
 * Owns the reactive quote/fee orchestration that used to live inline in [SwapFormViewModel] as the
 * `calculateFees()` god-method: a single `combine`/`debounce`/`collectLatest` pipeline wiring token
 * + amount + refresh-timer inputs to quote fetching and fee/UI updates (#4735).
 *
 * The ViewModel stays a thin coordinator: it builds a [SwapQuoteContext] over its own state flows
 * and calls [start]. Every state write the pipeline performs targets the ViewModel's flows
 * verbatim, preserving the UTXO plan-fee race, the `isLoading` spinner timing (#4712), and the
 * `isSwapDisabled` gating exactly as before.
 */
internal class SwapQuoteOrchestrator
@Inject
constructor(
    private val swapQuoteRepository: SwapQuoteRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val getDiscountBpsUseCase: GetDiscountBpsUseCase,
    private val referralRepository: ReferralCodeSettingsRepository,
    private val convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase,
    private val swapDiscountChecker: SwapDiscountChecker,
    private val swapGasCalculator: SwapGasCalculator,
    private val swapValidator: SwapValidator,
    private val fiatValueToString: FiatValueToStringMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private lateinit var scope: CoroutineScope
    private lateinit var context: SwapQuoteContext

    private val referralCode = MutableStateFlow<String?>(null)

    private var refreshQuoteJob: Job? = null

    private data class QuoteInput(
        val address: Pair<SendSrc, SendSrc>,
        val amount: BigDecimal?,
        // True when the change should bypass the typing debounce (percentage / Max / paste).
        val immediate: Boolean,
    )

    /**
     * Launches the quote/fee pipeline on [scope], reading and writing the flows in [context]. Call
     * once during ViewModel initialization (mirroring the original inline `calculateFees()` call).
     */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope, context: SwapQuoteContext) {
        this.scope = scope
        this.context = context

        val swapQuoteManager = context.swapQuoteManager
        val uiState = context.uiState
        val quoteState = context.quoteState
        val swapFeeFiat = context.swapFeeFiat
        val estimatedNetworkFeeTokenValue = context.estimatedNetworkFeeTokenValue
        val estimatedNetworkFeeFiatValue = context.estimatedNetworkFeeFiatValue
        val gasFee = context.gasFee
        val gasFeeChain = context.gasFeeChain
        val refreshQuoteState = context.refreshQuoteState
        val selectedSrc = context.selectedSrc
        val selectedDst = context.selectedDst

        scope.safeLaunch {
            // Emits once per source-amount change carrying whether the quote should fetch
            // immediately (percentage / Max / paste) instead of waiting out the typing debounce.
            // Empty input still flows through so collectLatest hits the zero-amount branch and
            // resetQuoteState() clears the stale quote (#4712).
            val amountChanges = swapQuoteManager.amountChanges(context.srcAmountTextFlow)

            combine(selectedSrc.filterNotNull(), selectedDst.filterNotNull()) { src, dst ->
                    src to dst
                }
                .distinctUntilChanged()
                .onEach {
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
                }
                .combine(amountChanges) { address, immediate ->
                    QuoteInput(
                        address = address,
                        amount = context.srcAmount(),
                        immediate = immediate,
                    )
                }
                // Fires on real user intent (typing, paste, percentage, token change) but not on
                // the
                // silent refreshes combined in below — so the spinner appears immediately ahead of
                // the debounce and an instant indicative estimate fills the destination field while
                // we wait, without flashing on background refreshes (#4712).
                .onEach { input ->
                    uiState.update { it.copy(isLoading = true) }
                    showIndicativeRate(input)
                }
                .combine(refreshQuoteState) { input, _ -> input }
                // Percentage / Max / paste skip the debounce (0ms); free typing still coalesces at
                // 300ms so rapid edits fire a single quote fetch.
                .debounce { input -> swapQuoteManager.quoteDebounceMillis(input.immediate) }
                // collectLatest so newer input cancels an in-flight fetch instead of letting a
                // stale fetch write isLoading = false after the user has already typed again.
                .collectLatest { input ->
                    val (src, dst) = input.address
                    val amount = input.amount

                    val srcToken = src.account.token
                    val dstToken = dst.account.token

                    val srcTokenValue =
                        amount?.movePointRight(src.account.token.decimal)?.toBigInteger()

                    // An empty field (the initial state on entry, or a cleared field) is not an
                    // error. The empty-input filter was removed so clearing the field clears the
                    // stale quote (#4712); without this guard that same empty emission would throw
                    // AmountCannotBeZero and flash "Invalid amount" the moment the screen opens.
                    // Clear the quote silently and wait for a real amount instead. An explicitly
                    // entered zero still falls through to the AmountCannotBeZero error below.
                    if (context.isSrcAmountEmpty()) {
                        resetQuoteState(error = null, cause = null, tag = null)
                        return@collectLatest
                    }

                    try {
                        if (srcTokenValue == null || srcTokenValue <= BigInteger.ZERO) {
                            throw SwapException.AmountCannotBeZero("Amount must be positive")
                        }
                        if (srcToken == dstToken) {
                            throw SwapException.SameAssets("Can't swap same assets ${srcToken.id})")
                        }

                        val tokenValue = convertTokenAndValueToTokenValue(srcToken, srcTokenValue)

                        val eligibleProviders =
                            swapQuoteRepository.getEligibleProviders(srcToken, dstToken)
                        if (eligibleProviders.isEmpty()) {
                            throw SwapException.SwapIsNotSupported(
                                "Swap is not supported for this pair"
                            )
                        }

                        val currency = appCurrencyRepository.currency.first()

                        val baselineReferral =
                            referralCode.value
                                ?: context.vaultId()?.let {
                                    referralRepository.getExternalReferralBy(it)
                                }

                        val candidates = coroutineScope {
                            eligibleProviders
                                .map { p ->
                                    async {
                                        val discount =
                                            context.vaultId()?.let { id ->
                                                getDiscountBpsUseCase.invoke(id, p).takeIf { bps ->
                                                    bps != 0
                                                }
                                            }
                                        QuoteCandidate(
                                            provider = p,
                                            vultBPSDiscount = discount,
                                            referral = baselineReferral,
                                        )
                                    }
                                }
                                .awaitAll()
                        }

                        val resolution =
                            swapQuoteManager.resolveBestQuote(
                                candidates = candidates,
                                src = src,
                                dst = dst,
                                srcToken = srcToken,
                                dstToken = dstToken,
                                srcTokenValue = srcTokenValue,
                                tokenValue = tokenValue,
                                currency = currency,
                                amount = amount,
                                selectedSrcTokenTitle = context.selectedSrcTokenTitle(),
                            )
                        // Map the sealed result: a typed fetch failure resets the quote state with
                        // its already-mapped error; only a Success continues into fee processing.
                        val bestQuote =
                            when (resolution) {
                                is QuoteResolution.Failure -> {
                                    resetQuoteState(
                                        error = resolution.formError,
                                        cause = resolution.cause,
                                        tag = resolution.tag,
                                    )
                                    return@collectLatest
                                }
                                is QuoteResolution.Success -> resolution.best
                            }

                        val quoteResult = bestQuote.result
                        val provider = quoteResult.provider
                        quoteState.provider = provider

                        val vultBPSDiscount = bestQuote.candidate.vultBPSDiscount
                        val referral = bestQuote.candidate.referral

                        if (provider == SwapProvider.THORCHAIN) {
                            referral?.let { code ->
                                val tierType = vultBPSDiscount?.getTierType()
                                val result =
                                    swapDiscountChecker.checkReferralBpsDiscount(
                                        tierType,
                                        srcToken,
                                        tokenValue,
                                        code,
                                    )
                                result.referralCode?.let { rc -> referralCode.update { rc } }
                                uiState.update {
                                    it.copy(
                                        discountInfo =
                                            it.discountInfo.copy(
                                                referralBpsDiscount = result.referralBpsDiscount,
                                                referralBpsDiscountFiatValue =
                                                    result.referralBpsDiscountFiatValue,
                                            )
                                    )
                                }
                            }
                        } else {
                            uiState.update {
                                it.copy(
                                    discountInfo =
                                        it.discountInfo.copy(
                                            referralBpsDiscount = null,
                                            referralBpsDiscountFiatValue = null,
                                        )
                                )
                            }
                        }

                        val vultResult =
                            swapDiscountChecker.checkVultBpsDiscount(
                                srcToken,
                                tokenValue,
                                vultBPSDiscount,
                            )
                        uiState.update {
                            it.copy(
                                discountInfo =
                                    it.discountInfo.copy(
                                        vultBpsDiscount = vultResult.vultBpsDiscount,
                                        vultBpsDiscountFiatValue =
                                            vultResult.vultBpsDiscountFiatValue,
                                        tierType = vultResult.tierType,
                                    )
                            )
                        }

                        quoteState.quote = quoteResult.quote
                        // SwapKit BTC settles by broadcasting the provider's PSBT, whose miner fee
                        // is the only network cost — and it is already surfaced as the UTXO plan
                        // network fee below. SwapKit reports that same deposit cost as its inbound
                        // fee, so counting it again as a swap fee would double-count the BTC
                        // network
                        // cost in the headline total (iOS shows it once). Zero the swap-fee
                        // contribution and the breakdown row so Total reconciles to Network Fee
                        // alone; the affiliate fee is already baked into expectedDstValue.
                        val isSwapKitUtxoSwap =
                            quoteResult.quote is SwapQuote.SwapKit &&
                                srcToken.chain.standard == TokenStandard.UTXO
                        val effectiveSwapFeeFiat =
                            if (isSwapKitUtxoSwap)
                                FiatValue(BigDecimal.ZERO, quoteResult.swapFeeFiat.currency)
                            else quoteResult.swapFeeFiat
                        val feeText =
                            if (isSwapKitUtxoSwap)
                                fiatValueToString(effectiveSwapFeeFiat, asFee = true)
                            else quoteResult.feeText
                        swapFeeFiat.value = effectiveSwapFeeFiat

                        // Determine destination address and memo for UTXO plan fee computation.
                        // Must be computed before the uiState.update so the button stays
                        // disabled for UTXO swaps until the plan fee is verified.
                        val utxoFeeData: Pair<String, String?>? =
                            when (val q = quoteResult.quote) {
                                is SwapQuote.ThorChain ->
                                    (q.data.router
                                        ?: q.data.inboundAddress
                                        ?: src.address.address) to q.data.memo
                                is SwapQuote.MayaChain ->
                                    (q.data.inboundAddress ?: src.address.address) to q.data.memo
                                // SwapKit BTC is a PSBT deposit to targetAddress; route it through
                                // the same UTXO plan-fee path so the network fee is computed and
                                // swap() doesn't abort with invalid_gas_fee_calculation.
                                is SwapQuote.SwapKit ->
                                    if (srcToken.chain.standard == TokenStandard.UTXO) {
                                        q.data.targetAddress to q.data.memo
                                    } else null
                                else -> null
                            }
                        val isUtxoSwap =
                            utxoFeeData != null &&
                                srcToken.chain.standard == TokenStandard.UTXO &&
                                srcToken.chain != Chain.Cardano

                        // For UTXO swaps keep isSwapDisabled=true until plan fee is verified
                        // so a tap between this update and the plan-fee write never submits
                        // with sats/byte as the total fee.
                        uiState.update {
                            it.copy(
                                srcFiatValue = quoteResult.srcFiatValueText,
                                quoteDisplay =
                                    it.quoteDisplay.copy(
                                        provider = quoteResult.providerUiText,
                                        estimatedDstTokenValue = quoteResult.estimatedDstTokenValue,
                                        estimatedDstFiatValue = quoteResult.estimatedDstFiatValue,
                                        isDstEstimated = false,
                                        hasQuote = true,
                                        expiredAt = quoteState.quote?.expiredAt,
                                    ),
                                feeBreakdown =
                                    it.feeBreakdown.copy(
                                        fee = feeText,
                                        outboundFee = quoteResult.outboundFeeText,
                                        swapFeePercent = quoteResult.swapFeePercent,
                                    ),
                                formError = null,
                                isSwapDisabled = isUtxoSwap,
                                isLoading = false,
                            )
                        }

                        if (isUtxoSwap) {
                            val currentGasFee =
                                gasFee.value?.takeIf { gasFeeChain.value == srcToken.chain }
                            val currentVaultId = context.vaultId()
                            if (currentGasFee != null && currentVaultId != null) {
                                val (utxoDstAddress, utxoMemo) = utxoFeeData!!
                                when (
                                    val planFee =
                                        swapGasCalculator.resolveUtxoPlanFee(
                                            vaultId = currentVaultId,
                                            srcToken = srcToken,
                                            srcAddress = src.address.address,
                                            dstAddress = utxoDstAddress,
                                            memo = utxoMemo,
                                            tokenAmountInt = srcTokenValue,
                                            gasFee = currentGasFee,
                                        )
                                ) {
                                    is UtxoPlanFeeResult.Success -> {
                                        estimatedNetworkFeeFiatValue.value =
                                            planFee.estimated.fiatValue
                                        estimatedNetworkFeeTokenValue.value =
                                            planFee.estimated.tokenValue
                                        uiState.update {
                                            it.copy(
                                                feeBreakdown =
                                                    it.feeBreakdown.copy(
                                                        networkFee =
                                                            planFee.estimated.formattedTokenValue,
                                                        networkFeeFiat =
                                                            planFee.estimated.formattedFiatValue,
                                                    ),
                                                isSwapDisabled = false,
                                            )
                                        }
                                    }
                                    UtxoPlanFeeResult.InsufficientUtxos -> {
                                        uiState.update {
                                            it.copy(
                                                isSwapDisabled = true,
                                                formError =
                                                    UiText.StringResource(
                                                        R.string.insufficient_utxos_error
                                                    ),
                                            )
                                        }
                                    }
                                    UtxoPlanFeeResult.Unavailable -> {
                                        estimatedNetworkFeeTokenValue.value = null
                                        estimatedNetworkFeeFiatValue.value = null
                                        uiState.update {
                                            it.copy(
                                                isSwapDisabled = true,
                                                feeBreakdown =
                                                    it.feeBreakdown.copy(
                                                        networkFee = "",
                                                        networkFeeFiat = "",
                                                    ),
                                            )
                                        }
                                    }
                                }
                            } else {
                                // gasFeeChain lags srcToken.chain after a token switch:
                                // clear any stale fee from the previous chain.
                                estimatedNetworkFeeTokenValue.value = null
                                estimatedNetworkFeeFiatValue.value = null
                                uiState.update {
                                    it.copy(
                                        feeBreakdown =
                                            it.feeBreakdown.copy(
                                                networkFee = "",
                                                networkFeeFiat = "",
                                            )
                                    )
                                }
                            }
                        }

                        val balanceError =
                            swapValidator.validateBalanceForSwap(
                                src,
                                srcTokenValue,
                                estimatedNetworkFeeTokenValue.value,
                            )
                        if (balanceError != null) {
                            uiState.update {
                                it.copy(isSwapDisabled = true, formError = balanceError.formError)
                            }
                        }
                    } catch (e: SwapException) {
                        resetQuoteState(
                            error =
                                swapQuoteManager.mapSwapExceptionToFormError(
                                    e,
                                    srcToken,
                                    context.selectedSrcTokenTitle(),
                                ),
                            cause = e,
                            tag = "swapError",
                        )
                    } catch (e: SwapKitError) {
                        resetQuoteState(
                            error = swapQuoteManager.mapSwapKitErrorToFormError(e),
                            cause = e,
                            tag = "swapKitError",
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        resetQuoteState(
                            error = UiText.StringResource(R.string.swap_error_quote_failed),
                            cause = e,
                            tag = "swapUnexpectedError",
                        )
                    }

                    quoteState.quote?.expiredAt?.let { launchRefreshQuoteTimer(it) }
                }
        }
    }

    /**
     * Resets the quote state to its pristine, no-quote form: cancels the refresh timer, drops the
     * cached quote/provider/swap-fee, and clears the quote-related UI. Called by the pipeline on
     * empty input / fetch failure, and by [SwapFormViewModel] from the flip gesture.
     */
    fun resetQuoteState() {
        resetQuoteState(error = null, cause = null, tag = null)
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
                context.swapQuoteManager.computeIndicativeQuote(
                    srcToken,
                    dstToken,
                    amount,
                    currency,
                ) ?: return

            context.uiState.update {
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
                context.refreshQuoteState.value++
            }
    }

    private fun resetQuoteState(error: UiText?, cause: Throwable?, tag: String?) {
        // The prior quote's refresh timer would otherwise fire mid-flip/mid-error and re-run
        // calculateFees against the same invalid amount, briefly re-exposing the fee block.
        refreshQuoteJob?.cancel()
        refreshQuoteJob = null
        context.quoteState.quote = null
        context.quoteState.provider = null
        // collectTotalFee() combines this with estimatedNetworkFeeFiatValue. Resetting it
        // to null lets filterNotNull() short-circuit so a later calculateGas() update can't
        // write a (newGas + staleSwap) combination back into state.totalFee — the same race
        // that triggers on flipSelectedTokens since selectedSrc changes synchronously.
        context.swapFeeFiat.value = null
        // networkFee/networkFeeFiat are tied to the source token (calculateGas), not to a
        // specific quote, so we deliberately leave them alone — resetting them would leave
        // them empty until selectedSrc changes again.
        context.uiState.update {
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
