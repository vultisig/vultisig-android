package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.swap.SwapTokenSelector.Companion.ARG_SELECTED_DST_TOKEN_ID
import com.vultisig.wallet.ui.models.swap.SwapTokenSelector.Companion.ARG_SELECTED_SRC_TOKEN_ID
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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

@HiltViewModel
internal class SwapFormViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val fiatValueToString: FiatValueToStringMapper,
    private val convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase,
    private val swapQuoteRepository: SwapQuoteRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val swapTransactionRepository: SwapTransactionRepository,
    getDiscountBpsUseCase: GetDiscountBpsUseCase,
    referralRepository: ReferralCodeSettingsRepository,
    private val swapValidator: SwapValidator,
    swapDiscountChecker: SwapDiscountChecker,
    private val swapGasCalculator: SwapGasCalculator,
    private val swapTokenSelector: SwapTokenSelector,
    private val swapQuoteManager: SwapQuoteManager,
    private val swapTransactionBuilder: SwapTransactionBuilder,
    private val swapInputCollector: SwapInputCollector,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Swap>()

    // Constructed here (not Hilt-injected) so it shares the ViewModel's exact collaborator
    // instances — notably the cache-bearing swapQuoteManager, whose flip-quote cache would split
    // across two instances.
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

    private val _uiState = MutableStateFlow(SwapFormUiModel())

    /** Read-only swap form UI state; mutation is confined to this ViewModel via [_uiState]. */
    val uiState: StateFlow<SwapFormUiModel> = _uiState

    val srcAmountState = TextFieldState()

    private var vaultId: String? = null
    private val chain = MutableStateFlow<Chain?>(null)

    private val quoteState = QuoteStateHolder()

    private val srcAmount: BigDecimal?
        get() = srcAmountState.text.toString().toBigDecimalOrNull()

    private val selectedSrc = MutableStateFlow<SendSrc?>(null)
    private val selectedDst = MutableStateFlow<SendSrc?>(null)
    private val selectedSrcId = MutableStateFlow<String?>(null)
    private val selectedDstId = MutableStateFlow<String?>(null)
    private val referralCode = MutableStateFlow<String?>(null)

    private val estimatedNetworkFeeTokenValue = MutableStateFlow<TokenValue?>(null)
    private val gasFee = MutableStateFlow<TokenValue?>(null)
    private val gasFeeChain = MutableStateFlow<Chain?>(null)
    private val estimatedNetworkFeeFiatValue = MutableStateFlow<FiatValue?>(null)

    private val addresses = MutableStateFlow<List<Address>>(emptyList())

    private val refreshQuoteState = MutableStateFlow(0)

    private var selectTokensJob: Job? = null

    private var refreshQuoteJob: Job? = null

    // Whether the currently selected source/destination pair has any eligible swap provider.
    // Resolved up front on every pair change (#4710) so an unroutable pair surfaces guidance the
    // moment it is selected and never reaches the quote pipeline (which would throw
    // SwapIsNotSupported only after the user typed an amount and waited out the debounce).
    private var isPairSupported = true

    private val pairNotSupportedError = UiText.StringResource(R.string.swap_route_not_available)

    private var isLoading: Boolean
        get() = _uiState.value.isLoading
        set(value) {
            _uiState.update { it.copy(isLoading = value) }
        }

    private var isLoadingNextScreen: Boolean
        get() = _uiState.value.isLoadingNextScreen
        set(value) {
            _uiState.update { it.copy(isLoadingNextScreen = value) }
        }

    init {
        viewModelScope.launch {
            loadData(
                vaultId = args.vaultId,
                chainId = args.chainId,
                srcTokenId = args.srcTokenId,
                dstTokenId = args.dstTokenId,
            )
        }

        swapTokenSelector.collectSelectedAccounts(
            selectedSrc,
            selectedDst,
            _uiState,
            viewModelScope,
        )
        collectSelectedTokens()

        calculateGas()
        observeQuotePipeline()
        collectTotalFee()
    }

    fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    fun swap() {
        val inputs =
            try {
                isLoadingNextScreen = true
                swapInputCollector.collect(
                    vaultId = vaultId,
                    selectedSrc = selectedSrc.value,
                    selectedDst = selectedDst.value,
                    srcAmount = srcAmountState.text.toString(),
                    quote = quoteState.quote,
                    gasFee = gasFee.value,
                    estimatedNetworkFeeTokenValue = estimatedNetworkFeeTokenValue.value,
                    estimatedNetworkFeeFiatValue = estimatedNetworkFeeFiatValue.value,
                )
            } catch (e: InvalidTransactionDataException) {
                isLoadingNextScreen = false
                showError(e.text)
                return
            } catch (e: Exception) {
                isLoadingNextScreen = false
                Timber.e(e)
                showError(UiText.StringResource(R.string.swap_screen_invalid_quote_calculation))
                return
            }

        viewModelScope.safeLaunch(
            onError = { e ->
                isLoadingNextScreen = false
                if (e is InvalidTransactionDataException) {
                    showError(e.text)
                } else {
                    Timber.e(e)
                    showError(UiText.StringResource(R.string.swap_screen_invalid_quote_calculation))
                }
            }
        ) {
            val transaction =
                swapTransactionBuilder.build(
                    vaultId = inputs.vaultId,
                    srcToken = inputs.srcToken,
                    dstToken = inputs.dstToken,
                    srcAddress = inputs.srcAddress,
                    srcTokenValue = inputs.srcTokenValue,
                    quote = inputs.quote,
                    gasFee = inputs.gasFee,
                    gasFeeFiatValue = inputs.gasFeeFiatValue,
                    estimatedNetworkFeeTokenValue = inputs.estimatedNetworkFeeTokenValue,
                    estimatedNetworkFeeFiatValue = inputs.estimatedNetworkFeeFiatValue,
                )

            swapTransactionRepository.addTransaction(transaction)

            navigator.route(
                Route.VerifySwap(transactionId = transaction.id, vaultId = inputs.vaultId)
            )
            isLoadingNextScreen = false
        }
    }

    fun selectSrcNetwork() {
        viewModelScope.launch {
            val newSendSrc =
                swapTokenSelector.selectNetwork(
                    vaultId = vaultId ?: return@launch,
                    selectedChain = selectedSrc.value?.address?.chain ?: return@launch,
                    addresses = addresses.value,
                ) ?: return@launch
            selectedSrcId.value = newSendSrc.account.token.id
        }
    }

    fun selectSrcNetworkPopup(offset: Offset) {
        viewModelScope.launch {
            val newSendSrc =
                swapTokenSelector.selectNetworkPopup(
                    vaultId = vaultId ?: return@launch,
                    selectedChain = selectedSrc.value?.address?.chain ?: return@launch,
                    position = offset,
                    addresses = addresses.value,
                ) ?: return@launch
            selectedSrcId.value = newSendSrc.account.token.id
        }
    }

    fun selectDstNetwork() {
        viewModelScope.launch {
            val newSendSrc =
                swapTokenSelector.selectNetwork(
                    vaultId = vaultId ?: return@launch,
                    selectedChain = selectedDst.value?.address?.chain ?: return@launch,
                    addresses = addresses.value,
                ) ?: return@launch
            selectedDstId.value = newSendSrc.account.token.id
        }
    }

    fun selectDstNetworkPopup(position: Offset) {
        viewModelScope.launch {
            val newSendSrc =
                swapTokenSelector.selectNetworkPopup(
                    vaultId = vaultId ?: return@launch,
                    selectedChain = selectedDst.value?.address?.chain ?: return@launch,
                    position = position,
                    addresses = addresses.value,
                ) ?: return@launch
            selectedDstId.value = newSendSrc.account.token.id
        }
    }

    fun selectSrcToken() {
        navigateToSelectToken(ARG_SELECTED_SRC_TOKEN_ID)
    }

    fun selectDstToken() {
        navigateToSelectToken(ARG_SELECTED_DST_TOKEN_ID)
    }

    private fun navigateToSelectToken(targetArg: String) {
        viewModelScope.launch {
            swapTokenSelector.navigateToSelectToken(
                targetArg = targetArg,
                vaultId = vaultId ?: return@launch,
                selectedSrc = selectedSrc.value,
                selectedDst = selectedDst.value,
                selectedSrcId = selectedSrcId,
                selectedDstId = selectedDstId,
                addresses = addresses,
                uiState = _uiState,
            )
        }
    }

    fun flipSelectedTokens() {
        cacheCurrentQuote()

        val currentSrcText = srcAmountState.text.toString()
        val currentSrcTokenId = selectedSrc.value?.account?.token?.id
        val currentDstTokenId = selectedDst.value?.account?.token?.id

        val restoredAmount =
            quoteState.preFlipState
                ?.takeIf { state ->
                    state.srcTokenId == currentDstTokenId &&
                        state.dstTokenId == currentSrcTokenId &&
                        state.flippedAmount == currentSrcText
                }
                ?.srcAmount

        val newSrcAmount =
            restoredAmount
                ?: quoteState.quote
                    ?.expectedDstValue
                    ?.decimal
                    ?.formatFlippedAmount(selectedDst.value?.account?.token?.decimal)

        resetQuoteState()

        // Fall back to the raw ID when the resolved SendSrc hasn't loaded yet, so a race between
        // the flip gesture and token resolution never silently clobbers both slots with null.
        val newSrcId = currentDstTokenId ?: selectedDstId.value
        val newDstId = currentSrcTokenId ?: selectedSrcId.value
        selectedSrcId.value = newSrcId
        selectedDstId.value = newDstId

        // collectSelectedTokens() observes the IDs above and resolves selectedSrc/selectedDst
        // synchronously under Main.immediate. A manual swap of those resolved StateFlows here
        // would read the already-resolved post-swap values and write them back into their
        // original slots, silently reverting the flip so the UI shows the original pair.

        if (
            newSrcAmount != null &&
                newSrcAmount.toBigDecimalOrNull().let { it != null && it > BigDecimal.ZERO }
        ) {
            srcAmountState.setTextAndPlaceCursorAtEnd(newSrcAmount)
        }

        quoteState.preFlipState =
            if (currentSrcTokenId != null && currentDstTokenId != null) {
                PreFlipState(
                    srcAmount = currentSrcText,
                    srcTokenId = currentSrcTokenId,
                    dstTokenId = currentDstTokenId,
                    flippedAmount = newSrcAmount ?: currentSrcText,
                )
            } else null
    }

    private fun cacheCurrentQuote() {
        val currentQuote = quoteState.quote ?: return
        val currentProvider = quoteState.provider ?: return
        val srcToken = selectedSrc.value?.account?.token ?: return
        val dstToken = selectedDst.value?.account?.token ?: return
        val currentAmount = srcAmount?.movePointRight(srcToken.decimal)?.toBigInteger() ?: return

        swapQuoteManager.cacheQuote(
            currentQuote,
            currentProvider,
            srcToken.id,
            dstToken.id,
            srcToken.address,
            dstToken.address,
            currentAmount,
        )
    }

    private fun resetQuoteState() {
        resetQuoteState(error = null, cause = null, tag = null)
    }

    fun selectSrcPercentage(percentage: Float) {
        val selectedSrcAccount = selectedSrc.value?.account ?: return
        val srcTokenValue = selectedSrcAccount.tokenValue ?: return

        val srcToken = selectedSrcAccount.token

        val swapFee =
            quoteState.quote?.fees?.value.takeIf { quoteState.provider == SwapProvider.LIFI }
                ?: BigInteger.ZERO

        val maxUsableTokenAmount =
            srcTokenValue.value -
                swapFee -
                (estimatedNetworkFeeTokenValue.value?.value?.takeIf {
                    srcToken.isNativeToken && gasFeeChain.value == srcToken.chain
                } ?: BigInteger.ZERO)

        if (maxUsableTokenAmount <= BigInteger.ZERO) {
            srcAmountState.setTextAndPlaceCursorAtEnd("0")
            val errorRes =
                if (srcToken.isNativeToken) {
                    R.string.swap_error_insufficient_balance_and_fees
                } else {
                    R.string.swap_error_insufficient_source_token
                }
            showError(UiText.FormattedText(errorRes, listOf(srcToken.ticker)))
            return
        }

        val amount =
            TokenValue.createDecimal(maxUsableTokenAmount, srcTokenValue.decimals)
                .multiply(percentage.toBigDecimal())
                .formatFlippedAmount(srcTokenValue.decimals)

        // A percentage / Max tap is an explicit, deliberate amount — fetch the quote immediately
        // instead of waiting out the typing debounce (#4712). Mark before mutating the text so the
        // resulting emission is already marked immediate.
        swapQuoteManager.markImmediateFetch()
        srcAmountState.setTextAndPlaceCursorAtEnd(amount)
    }

    fun loadData(vaultId: String, chainId: String?, srcTokenId: String?, dstTokenId: String?) {
        this.chain.value = chainId?.let(Chain::fromRaw)

        if (!srcTokenId.isNullOrBlank() && this.selectedSrcId.value == null) {
            selectedSrcId.value = srcTokenId
        }

        if (!dstTokenId.isNullOrBlank() && this.selectedDstId.value == null) {
            selectedDstId.value = dstTokenId
        }

        if (this.vaultId != vaultId) {
            this.vaultId = vaultId
            swapTokenSelector.loadTokens(vaultId, addresses, viewModelScope)
        }
    }

    fun validateAmount() {
        val errorMessage = swapValidator.validateSrcAmount(srcAmountState.text.toString())
        _uiState.update { it.copy(error = errorMessage) }
    }

    private fun collectSelectedTokens() {
        selectTokensJob =
            swapTokenSelector.collectSelectedTokens(
                addresses,
                selectedSrcId,
                selectedDstId,
                selectedSrc,
                selectedDst,
                chain,
                selectTokensJob,
                viewModelScope,
            )
    }

    private fun calculateGas() {
        viewModelScope.launch {
            selectedSrc
                .filterNotNull()
                .map { sendSrc ->
                    val vaultId = vaultId ?: return@map null
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

                            _uiState.update {
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
                        _uiState.update {
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
                _uiState.update {
                    it.copy(
                        feeBreakdown =
                            it.feeBreakdown.copy(
                                totalFee = fiatValueToString(totalFee, asFee = true)
                            )
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Wires the source-amount / token-selection flows to the quote pipeline: shows the loading
     * spinner and an indicative rate on real user intent, debounces, then hands each input to
     * [SwapQuotePipeline] and applies its result (quote display, discounts, fees, refresh timer).
     * Named for the whole pipeline it drives — fees are only one part of what it produces.
     */
    @OptIn(FlowPreview::class)
    private fun observeQuotePipeline() {
        viewModelScope.safeLaunch {
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
                    _uiState.update {
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
                                vaultId = vaultId,
                                referralCode = referralCode.value,
                                currentDiscountInfo = _uiState.value.discountInfo,
                                selectedSrcTokenTitle = _uiState.value.selectedSrcToken?.title,
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
        result.referralCodeToStore?.let { rc -> referralCode.update { rc } }
        quoteState.swapFeeFiat.value = result.swapFeeFiat

        _uiState.update {
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
                vaultId = vaultId,
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
        _uiState.update {
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

            _uiState.update {
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
            viewModelScope.launch(ioDispatcher) {
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
        } else if (_uiState.value.formError == pairNotSupportedError) {
            // Moving from an unroutable pair to a routable one clears the stale guidance at once,
            // ahead of the debounced quote that would otherwise clear it ~300ms later.
            _uiState.update { it.copy(formError = null) }
        }
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
        _uiState.update {
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

    fun hideError() {
        _uiState.update { it.copy(error = null, formError = null) }
    }

    private fun showError(error: UiText) {
        _uiState.update { it.copy(error = error) }
    }

    companion object {
        const val ETH_GAS_LIMIT: Long = SwapGasCalculator.ETH_GAS_LIMIT
        const val ARB_GAS_LIMIT: Long = SwapGasCalculator.ARB_GAS_LIMIT
    }
}
