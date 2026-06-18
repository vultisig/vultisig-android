package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.swap.SwapTokenSelector.Companion.ARG_SELECTED_DST_TOKEN_ID
import com.vultisig.wallet.ui.models.swap.SwapTokenSelector.Companion.ARG_SELECTED_SRC_TOKEN_ID
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
internal class SwapFormViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val swapValidator: SwapValidator,
    private val swapTokenSelector: SwapTokenSelector,
    private val swapQuoteManager: SwapQuoteManager,
    private val swapTransactionBuilder: SwapTransactionBuilder,
    private val swapInputCollector: SwapInputCollector,
    private val swapQuotePipelineControllerFactory: SwapQuotePipelineController.Factory,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Swap>()

    private val _uiState = MutableStateFlow(SwapFormUiModel())

    /** Read-only swap form UI state; mutation is confined to this ViewModel via [_uiState]. */
    val uiState: StateFlow<SwapFormUiModel> = _uiState

    val srcAmountState = TextFieldState()

    private var vaultId: String? = null
    private val chain = MutableStateFlow<Chain?>(null)

    private val srcAmount: BigDecimal?
        get() = srcAmountState.text.toString().toBigDecimalOrNull()

    private val selectedSrc = MutableStateFlow<SendSrc?>(null)
    private val selectedDst = MutableStateFlow<SendSrc?>(null)
    private val selectedSrcId = MutableStateFlow<String?>(null)
    private val selectedDstId = MutableStateFlow<String?>(null)
    private val referralCode = MutableStateFlow<String?>(null)

    // User-chosen slippage tolerance in basis points, or null for "Auto" (each provider keeps its
    // own default). Owned here and passed to the pipeline controller so a change re-fetches the
    // quote with the new tolerance (#4858).
    private val slippageBps = MutableStateFlow<Int?>(null)

    // Owns the gas / network-fee state and quote pipeline wiring (#4865). The ViewModel only reads
    // the resolved quote/fee values it exposes for swap(), the flip gesture, and percentage taps.
    private val quotePipeline =
        swapQuotePipelineControllerFactory.create(
            scope = viewModelScope,
            swapQuoteManager = swapQuoteManager,
            uiState = _uiState,
            selectedSrc = selectedSrc,
            selectedDst = selectedDst,
            referralCode = referralCode,
            slippageBps = slippageBps,
            srcAmountState = srcAmountState,
            vaultId = { vaultId },
            showError = ::showError,
        )

    private val quoteState
        get() = quotePipeline.quoteState

    private val addresses = MutableStateFlow<List<Address>>(emptyList())

    private var selectTokensJob: Job? = null

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

        quotePipeline.start()
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
                    gasFee = quotePipeline.gasFee.value,
                    estimatedNetworkFeeTokenValue =
                        quotePipeline.estimatedNetworkFeeTokenValue.value,
                    estimatedNetworkFeeFiatValue = quotePipeline.estimatedNetworkFeeFiatValue.value,
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

        quotePipeline.resetQuoteState()

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
            slippageBps.value,
        )
    }

    fun selectSrcPercentage(percentage: Float) {
        val selectedSrcAccount = selectedSrc.value?.account ?: return
        val srcTokenValue = selectedSrcAccount.tokenValue ?: return

        val srcToken = selectedSrcAccount.token

        // Each tap supersedes the previous one: clear any sticky error from an earlier tap so it
        // can't outlive the condition that raised it. The screen renders `error ?: formError`, so a
        // stale `error` (only ever cleared by an explicit dismiss) would otherwise pin a one-off
        // "insufficient balance" warning on a now-valid amount and mask the live quote/formError.
        _uiState.update { it.copy(error = null) }

        // The 25/50/75 chips take a plain fraction of the full balance, matching iOS and the
        // desktop app. Only MAX reserves the source-chain network fee, and only for a native source
        // on its own gas chain. The provider swap fee is taken from the destination amount (for
        // LI.FI it is denominated in the destination token's units), so it is never deducted from
        // the source balance here — that would mix decimals and could wrongly drive the usable
        // amount negative for a low-decimal source into a high-decimal destination.
        val reservedNetworkFee =
            if (percentage >= 1f) {
                quotePipeline.estimatedNetworkFeeTokenValue.value?.value?.takeIf {
                    srcToken.isNativeToken && quotePipeline.gasFeeChain.value == srcToken.chain
                } ?: BigInteger.ZERO
            } else {
                BigInteger.ZERO
            }
        val maxUsableTokenAmount = srcTokenValue.value - reservedNetworkFee

        if (maxUsableTokenAmount <= BigInteger.ZERO) {
            // Empty (not "0"): the empty-field path clears the stale quote silently, whereas a
            // literal "0" reaches the quote pipeline and throws/logs AmountCannotBeZero at ERROR
            // for an expected condition. The error set below stays visible to explain why.
            srcAmountState.setTextAndPlaceCursorAtEnd("")
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

    /**
     * Sets the per-swap slippage tolerance in basis points, or null for "Auto". Updates the
     * displayed value and re-fetches the quote with the new tolerance (#4858).
     */
    fun setSlippageBps(bps: Int?) {
        slippageBps.value = bps
        _uiState.update { it.copy(slippageBps = bps) }
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
