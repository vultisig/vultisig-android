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
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_TIER_THRESHOLD
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
import java.math.RoundingMode
import java.text.DecimalFormat
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val getDiscountBpsUseCase: GetDiscountBpsUseCase,
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

    // User EVM gas-limit override (units), or null for "Auto". Applied at transaction-build time
    // (no quote re-fetch needed), so it stays in the ViewModel rather than the quote pipeline
    // (#4858).
    private val gasLimitOverride = MutableStateFlow<Long?>(null)

    // Optional external recipient address (exactly as typed): drives the field display and the
    // swap() pre-flight gate. The swap output is routed here instead of the vault's own destination
    // address and stamped on the built transaction so it is shown on the verify screen (#4858).
    private val externalRecipient = MutableStateFlow<String?>(null)

    // The recipient actually fed into the quote pipeline: only a present-and-valid address (else
    // null = route to the vault). Gating here keeps partially-typed / invalid addresses from
    // triggering native (THOR/Maya) quote calls with a malformed destination (#4858 review).
    private val quoteRecipient = MutableStateFlow<String?>(null)

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
            externalRecipient = quoteRecipient,
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
        observeGasLimitApplicability()
        observeExternalRecipientValidity()

        quotePipeline.start()
    }

    /**
     * Tracks whether a custom gas limit applies to the selected source chain (EVM only) and clears
     * a stale override when switching to a non-EVM source, so it can never carry over to a chain
     * that ignores it (#4858).
     */
    private fun observeGasLimitApplicability() {
        viewModelScope.launch {
            combine(selectedSrc, quoteState.honorsGasLimitOverride) { src, honors ->
                    val isEvmSource = src?.account?.token?.chain?.standard == TokenStandard.EVM
                    // Drop a stale override only when leaving EVM entirely. A non-aggregator route
                    // (THOR/Maya) just disables the row — its value is kept for when an
                    // EVM-aggregator route returns, and the builder ignores it meanwhile.
                    if (!isEvmSource && gasLimitOverride.value != null) {
                        gasLimitOverride.value = null
                        _uiState.update { it.copy(gasLimitOverride = null) }
                    }
                    // Until a quote resolves (honors == null) stay applicable for an EVM source;
                    // once resolved, only an EVM-aggregator route honors the override.
                    isEvmSource && (honors ?: true)
                }
                .distinctUntilChanged()
                .collect { applicable ->
                    _uiState.update { it.copy(isGasLimitApplicable = applicable) }
                }
        }
    }

    /**
     * Re-validates the external recipient whenever the destination changes: a previously-valid
     * address can become invalid when the user switches the destination chain, so the inline error
     * (and the [swap] pre-flight gate) stay in sync with the current destination (#4858).
     */
    private fun observeExternalRecipientValidity() {
        viewModelScope.launch {
            // Re-sync routing too: a destination switch can flip the current recipient's validity,
            // which must add/remove it from the quote pipeline, not just the inline error.
            selectedDst.collect { syncExternalRecipientRouting() }
        }
    }

    /**
     * The address-format error for the current external recipient, or `null` when the recipient is
     * off or valid for the destination chain. Used both for inline feedback and as the [swap]
     * pre-flight gate so a malformed address can never be baked into the swap memo/destination.
     */
    private fun externalRecipientError(): UiText? {
        val address = externalRecipient.value ?: return null
        val dstChain = selectedDst.value?.account?.token?.chain ?: return null
        return if (chainAccountAddressRepository.isValid(dstChain, address)) {
            null
        } else {
            UiText.StringResource(R.string.swap_external_recipient_invalid)
        }
    }

    fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    fun swap() {
        // Hard gate: never stage a keysign that would route funds to a malformed recipient. The
        // initiator and joiner both sign this address from the shared payload, so an invalid value
        // must be caught here, before the transaction is built (#4858).
        externalRecipientError()?.let { error ->
            showError(error)
            return
        }

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
                    gasLimitOverride = gasLimitOverride.value,
                    externalRecipient = externalRecipient.value,
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

        // Key on the same effective destination the fetch path used (recipient when set, else the
        // vault address). Otherwise a recipient-routed quote would be cached under the bare
        // destination and a later no-recipient lookup could serve it, paying the cleared recipient.
        val cacheDstAddress = quoteRecipient.value?.takeIf { it.isNotBlank() } ?: dstToken.address

        swapQuoteManager.cacheQuote(
            currentQuote,
            currentProvider,
            srcToken.id,
            dstToken.id,
            srcToken.address,
            cacheDstAddress,
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
     *
     * Out-of-range values are rejected at this state boundary (only null or `1..10_000` bps, i.e.
     * 0.01%–100%) so no call site can push an invalid tolerance into the quote pipeline.
     */
    fun setSlippageBps(bps: Int?) {
        if (bps != null && bps !in 1..MAX_SLIPPAGE_BPS) return
        slippageBps.value = bps
        _uiState.update { it.copy(slippageBps = bps) }
    }

    /**
     * Sets the EVM gas-limit override in units, or null for "Auto". Applied when the swap
     * transaction is built; no quote re-fetch is needed (#4858).
     */
    fun setGasLimit(units: Long?) {
        gasLimitOverride.value = units
        _uiState.update { it.copy(gasLimitOverride = units) }
    }

    /**
     * Sets the external recipient address (blank/null = off). The swap output then routes to this
     * address; it is re-quoted and shown on the verify screen before signing (#4858).
     */
    fun setExternalRecipient(address: String?) {
        externalRecipient.value = address?.trim()?.takeIf { it.isNotEmpty() }
        syncExternalRecipientRouting()
    }

    /**
     * Reconciles the quote-routing recipient and the inline error with the typed recipient for the
     * current destination. Only a valid recipient is pushed into the quote pipeline; an invalid or
     * intermediate value routes quotes to the vault (null) instead of firing native quote calls
     * with a malformed destination (#4858 review). The typed value still drives the field and the
     * swap() pre-flight gate.
     */
    private fun syncExternalRecipientRouting() {
        val typed = externalRecipient.value
        val error = externalRecipientError()
        quoteRecipient.value = typed?.takeIf { error == null }
        _uiState.update { it.copy(externalRecipient = typed, externalRecipientError = error) }
    }

    /**
     * Advanced settings are gated behind the Silver VULT tier (>= 3000 VULT), mirroring iOS. An
     * entitled vault opens the sheet; a below-tier vault sees the upsell gate with its current
     * $VULT balance instead (#4858).
     */
    fun onAdvancedSettingsClicked() {
        val vaultId = vaultId ?: return
        viewModelScope.safeLaunch {
            if (getDiscountBpsUseCase.hasReachedSilverTier(vaultId)) {
                _uiState.update { it.copy(showAdvancedSettings = true) }
            } else {
                val balance = getDiscountBpsUseCase.getVultBalance(vaultId) ?: BigInteger.ZERO
                _uiState.update {
                    it.copy(
                        advancedSettingsGate =
                            VultTierGateUiModel(
                                balanceText = formatVultAmount(balance),
                                thresholdText = formatVultAmount(SILVER_TIER_THRESHOLD),
                                isBelowThreshold = true,
                            )
                    )
                }
            }
        }
    }

    fun dismissAdvancedSettings() {
        _uiState.update { it.copy(showAdvancedSettings = false) }
    }

    fun dismissAdvancedSettingsGate() {
        _uiState.update { it.copy(advancedSettingsGate = null) }
    }

    /**
     * Routes to a swap pre-filled with VULT as the destination so the user can top up their tier.
     */
    fun onGetVult() {
        val vaultId = vaultId ?: return
        _uiState.update { it.copy(advancedSettingsGate = null) }
        viewModelScope.launch {
            navigator.route(
                Route.Swap(
                    vaultId = vaultId,
                    chainId = Chain.Ethereum.id,
                    srcTokenId = Coins.Ethereum.ETH.id,
                    dstTokenId = Coins.Ethereum.VULT.id,
                )
            )
        }
    }

    private fun formatVultAmount(raw: BigInteger): String {
        val amount = BigDecimal(raw).movePointLeft(Coins.Ethereum.VULT.decimal)
        return "${VULT_DISPLAY_FORMAT.format(amount)} VULT"
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

        // Upper bound for slippage tolerance: 10_000 bps = 100%.
        private const val MAX_SLIPPAGE_BPS = 10_000

        // Grouped, up-to-8-decimal $VULT amount (e.g. "3,000", "6.65648001"); truncates rather than
        // rounds up so a displayed balance never overstates what the vault holds.
        private val VULT_DISPLAY_FORMAT =
            DecimalFormat("#,##0.########").apply { roundingMode = RoundingMode.DOWN }
    }
}
