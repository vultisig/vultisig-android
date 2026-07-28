package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.FeatureFlagRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.swap.limit.LimitSwapMarketPriceRepository
import com.vultisig.wallet.data.swap.limit.isThorchainRoutable
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_TIER_THRESHOLD
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.swap.SwapTokenSelector.Companion.ARG_SELECTED_DST_TOKEN_ID
import com.vultisig.wallet.ui.models.swap.SwapTokenSelector.Companion.ARG_SELECTED_SRC_TOKEN_ID
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.swap.SwapMode
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val featureFlagRepository: FeatureFlagRepository,
    private val limitMarketPriceRepository: LimitSwapMarketPriceRepository,
    private val buildLimitSwapTransactionUseCase: BuildLimitSwapTransactionUseCase,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val fiatValueToString: FiatValueToStringMapper,
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

    // THORChain limit-order ("Execute when") form state. Additive to the Market path — none of it
    // touches the quote pipeline; the whole tab is gated behind the remote limit-swap flag. The
    // flag
    // is a flow (not a plain var) so a late-resolving remote value re-triggers the market-price
    // fetch instead of leaving the tab enabled with no price.
    private val isLimitFlagEnabled = MutableStateFlow(false)
    private val swapMode = MutableStateFlow(SwapMode.Market)
    private val limitPriceUnit = MutableStateFlow(LimitPriceUnit.Fiat)
    private val limitPreset = MutableStateFlow<LimitPricePreset?>(LimitPricePreset.Market)
    private val limitExpiry = MutableStateFlow(LimitExpiryOption.TwentyFourHours)
    // Both prices are canonical: buy-asset units per 1 sell-asset unit (what the memo LIM needs).
    private val marketTargetPrice = MutableStateFlow<BigDecimal?>(null)
    private val limitTargetPrice = MutableStateFlow<BigDecimal?>(null)
    // App-currency price of one whole sell-asset unit, so the limit form's fiat display honors the
    // user's selected currency instead of hardcoding USD.
    private val sellUnitFiat = MutableStateFlow<FiatValue?>(null)
    private var marketPriceJob: Job? = null
    // The pair the current market/target prices belong to, so a pair change can invalidate them.
    private var pricedPairKey: String? = null
    private val assetFormat = DecimalFormat("#,##0.########")

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
        observeLimitForm()

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
     * Wires up the limit-order form. Entirely additive to the Market path: it reads the remote flag
     * once, fetches a reference price when the Limit tab is shown with a routable pair, and
     * recomputes the displayed values on any limit input change. Nothing here feeds the quote
     * pipeline or the Market swap.
     */
    private fun observeLimitForm() {
        viewModelScope.safeLaunch {
            isLimitFlagEnabled.value = featureFlagRepository.getFeatureFlags().isLimitSwapEnabled
        }
        // Fetch a fresh reference price whenever the Limit tab is shown with a routable pair. The
        // flag is part of the source set so a late-resolving remote flag re-fires the fetch.
        viewModelScope.launch {
            combine(isLimitFlagEnabled, swapMode, selectedSrc, selectedDst) {
                    flagEnabled,
                    mode,
                    src,
                    dst ->
                    LimitFetchTrigger(flagEnabled, mode, src, dst)
                }
                .distinctUntilChanged()
                .collectLatest { (flagEnabled, mode, src, dst) ->
                    val srcCoin = src?.account?.token
                    val dstCoin = dst?.account?.token
                    // Drop the previous pair's prices before refetching. They are quoted in the
                    // old pair's units, so leaving them live would keep the CTA enabled against a
                    // stale rate — and a tap during the fetch window would sign that wrong-pair
                    // rate into the memo's LIM. A custom (preset-less) price would otherwise never
                    // be replaced at all, since fetchMarketPrice only reseeds a null target.
                    val pairKey = pairKeyOf(srcCoin, dstCoin)
                    if (pairKey != pricedPairKey) {
                        pricedPairKey = pairKey
                        marketPriceJob?.cancel()
                        marketTargetPrice.value = null
                        limitTargetPrice.value = null
                    }
                    if (
                        mode == SwapMode.Limit &&
                            flagEnabled &&
                            srcCoin != null &&
                            dstCoin != null &&
                            isThorchainRoutable(srcCoin.chain) &&
                            isThorchainRoutable(dstCoin.chain)
                    ) {
                        fetchMarketPrice(srcCoin, dstCoin)
                    }
                    updateLimitOrderState()
                }
        }
        // Keep the sell asset's app-currency unit price current so fiat display honors the
        // currency.
        viewModelScope.launch {
            combine(selectedSrc, appCurrencyRepository.currency) { src, currency ->
                    src?.account?.token to currency
                }
                .collectLatest { (srcCoin, currency) ->
                    sellUnitFiat.value =
                        if (srcCoin == null) {
                            null
                        } else {
                            try {
                                convertTokenValueToFiat(
                                    srcCoin,
                                    TokenValue(
                                        BigInteger.TEN.pow(srcCoin.decimal),
                                        srcCoin.ticker,
                                        srcCoin.decimal,
                                    ),
                                    currency,
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to price sell asset for limit form")
                                null
                            }
                        }
                    updateLimitOrderState()
                }
        }
        viewModelScope.launch {
            combine(
                    limitTargetPrice,
                    limitPriceUnit,
                    limitPreset,
                    limitExpiry,
                    snapshotFlow { srcAmountState.text.toString() },
                ) { _, _, _, _, _ ->
                    Unit
                }
                .collect { updateLimitOrderState() }
        }
    }

    private data class LimitFetchTrigger(
        val flagEnabled: Boolean,
        val mode: SwapMode,
        val src: SendSrc?,
        val dst: SendSrc?,
    )

    fun onSelectSwapMode(mode: SwapMode) {
        swapMode.value = mode
        // The Limit form recomputes via the swapMode collector in observeLimitForm().
        _uiState.update { it.copy(swapMode = mode) }
    }

    fun onLimitPresetSelected(preset: LimitPricePreset) {
        limitPreset.value = preset
        marketTargetPrice.value?.let { market ->
            limitTargetPrice.value =
                LimitOrderPricing.applyPreset(market, preset.percentAboveMarket)
        }
    }

    fun onLimitExpirySelected(option: LimitExpiryOption) {
        limitExpiry.value = option
    }

    fun onLimitPriceUnitSelected(unit: LimitPriceUnit) {
        limitPriceUnit.value = unit
    }

    /**
     * Stages a THORChain limit order and routes to the confirmation screen. Reuses the market
     * swap's input validation ([SwapInputCollector]) for the resolved coins/amount/gas — the pair
     * is THORChain-routable so a market quote exists — then hands off to
     * [BuildLimitSwapTransactionUseCase], which builds the `=<` deposit and re-checks the advanced
     * swap queue. Placement flows through the same verify → keysign path as a market swap.
     */
    fun placeLimitOrder() {
        // Re-entry guard: the CTA disables off isLoadingNextScreen, but that flips inside this
        // method, so a fast second tap could otherwise start a second placement (a duplicate
        // on-chain deposit) before the UI updates.
        if (isLoadingNextScreen) return

        // Same hard gate as swap(): the recipient is baked into the `=<` memo's dest_addr, and it
        // survives a destination-chain change unvalidated, so a stale or malformed address must be
        // rejected here rather than signed into an order that pays out to nowhere.
        externalRecipientError()?.let { error ->
            showError(error)
            return
        }

        val targetPrice = limitTargetPrice.value
        if (targetPrice == null) {
            showError(UiText.StringResource(R.string.swap_screen_invalid_quote_calculation))
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
                buildLimitSwapTransactionUseCase.build(
                    BuildLimitSwapTransactionUseCase.Params(
                        vaultId = inputs.vaultId,
                        srcToken = inputs.srcToken,
                        dstToken = inputs.dstToken,
                        srcAddress = inputs.srcAddress,
                        srcTokenValue = inputs.srcTokenValue,
                        // Fail-safe: the memo only accepts 8 fractional digits, and rejects
                        // anything longer with an exception rather than rounding it itself.
                        targetPrice = LimitOrderPricing.toMemoScale(targetPrice),
                        expiryHours = limitExpiry.value.hours,
                        destinationAddress = externalRecipient.value ?: inputs.dstToken.address,
                        gasFee = inputs.gasFee,
                        gasFeeFiatValue = inputs.gasFeeFiatValue,
                        estimatedNetworkFeeTokenValue = inputs.estimatedNetworkFeeTokenValue,
                        estimatedNetworkFeeFiatValue = inputs.estimatedNetworkFeeFiatValue,
                    )
                )

            swapTransactionRepository.addTransaction(transaction)

            navigator.route(
                Route.VerifySwap(transactionId = transaction.id, vaultId = inputs.vaultId)
            )
            isLoadingNextScreen = false
        }
    }

    private fun pairKeyOf(srcCoin: Coin?, dstCoin: Coin?): String? =
        if (srcCoin == null || dstCoin == null) null else "${srcCoin.id}>${dstCoin.id}"

    /**
     * Fetches the affiliate-free reference price (buy units per sell unit) and seeds the preset.
     */
    private fun fetchMarketPrice(src: Coin, dst: Coin) {
        marketPriceJob?.cancel()
        marketPriceJob =
            viewModelScope.safeLaunch(
                onError = { Timber.w(it, "Failed to fetch limit-swap market price") }
            ) {
                val price =
                    limitMarketPriceRepository.getMarketPrice(
                        fromCoin = src,
                        toCoin = dst,
                        sourcePrice = src.usdPrice ?: BigDecimal.ZERO,
                    )
                if (price.signum() > 0) {
                    // The probe divides at scale 18; normalize here so the market price and every
                    // preset derived from it sit on the same 8-decimal grid the memo can express —
                    // otherwise the Market preset would read as *above* market and lose its
                    // "already available" warning.
                    val marketPrice = LimitOrderPricing.toMemoScale(price)
                    marketTargetPrice.value = marketPrice
                    val preset = limitPreset.value
                    if (preset != null || limitTargetPrice.value == null) {
                        limitTargetPrice.value =
                            LimitOrderPricing.applyPreset(
                                marketPrice,
                                (preset ?: LimitPricePreset.Market).percentAboveMarket,
                            )
                    }
                    updateLimitOrderState()
                }
            }
    }

    private suspend fun updateLimitOrderState() {
        val srcCoin = selectedSrc.value?.account?.token
        val dstCoin = selectedDst.value?.account?.token
        val enabled =
            isLimitFlagEnabled.value &&
                srcCoin != null &&
                dstCoin != null &&
                isThorchainRoutable(srcCoin.chain) &&
                isThorchainRoutable(dstCoin.chain)

        if (!enabled || srcCoin == null || dstCoin == null) {
            _uiState.update { it.copy(isLimitTabEnabled = enabled, limitOrder = null) }
            return
        }

        val target = limitTargetPrice.value
        val market = marketTargetPrice.value
        val sellAmount = srcAmountState.text.toString().toBigDecimalOrNull()
        // Fiat display uses the sell asset's app-currency unit price (not raw USD), matching the
        // Market swap flow so non-USD users see the correct symbol and value.
        val sellFiat = sellUnitFiat.value
        val fiatPerBuy = target?.let { LimitOrderPricing.fiatPricePerBuyUnit(it, sellFiat?.value) }
        val buyAmount = target?.let { LimitOrderPricing.expectedBuyAmount(sellAmount, it) }

        val fiatText = formatFiat(fiatPerBuy, sellFiat?.currency)
        val amountText =
            buyAmount?.let { "${formatAssetAmount(it)} ${dstCoin.ticker}" } ?: EMPTY_PRICE
        val unit = limitPriceUnit.value
        val priceText = if (unit == LimitPriceUnit.Fiat) fiatText else amountText
        val secondaryText = if (unit == LimitPriceUnit.Fiat) amountText else fiatText
        val warningRes =
            if (target != null && market != null) {
                when (LimitOrderPricing.warningFor(target, market)) {
                    LimitOrderPricing.LimitWarning.BelowMarket ->
                        R.string.limit_swap_warning_below_market
                    LimitOrderPricing.LimitWarning.FarAboveMarket ->
                        R.string.limit_swap_warning_far_above_market
                    null -> null
                }
            } else null

        // Use the resolved token logos the Market form uses (drawable/URL ImageModel), not the raw
        // Coin.logo name, which SubcomposeAsyncImage can't load — that is why the reference/asset
        // logos rendered as first-letter placeholders (#4154 UI).
        val current = _uiState.value
        val sellLogo = current.selectedSrcToken?.tokenLogo ?: srcCoin.logo
        val buyLogo = current.selectedDstToken?.tokenLogo ?: dstCoin.logo

        _uiState.update {
            it.copy(
                isLimitTabEnabled = true,
                limitOrder =
                    LimitOrderUiModel(
                        priceText = priceText,
                        referenceAmountLabel = "1 ${dstCoin.ticker}",
                        referenceLogo = buyLogo,
                        secondaryPriceLabel = secondaryText,
                        priceUnit = unit,
                        selectedPreset = limitPreset.value,
                        selectedExpiry = limitExpiry.value,
                        sellTicker = srcCoin.ticker,
                        sellLogo = sellLogo,
                        buyTicker = dstCoin.ticker,
                        buyLogo = buyLogo,
                        buyAmountText = buyAmount?.let(::formatAssetAmount) ?: "0",
                        warningRes = warningRes,
                        // Ready only when there is a resolved target price and a positive sell
                        // amount, so the CTA can't be tapped before the order can actually be
                        // built.
                        isPlaceOrderEnabled =
                            target != null && sellAmount != null && sellAmount.signum() > 0,
                    ),
            )
        }
    }

    /** Formats a fiat amount in the app currency, or [EMPTY_PRICE] when the price is unknown. */
    private suspend fun formatFiat(value: BigDecimal?, currency: String?): String =
        if (value != null && currency != null) {
            fiatValueToString(FiatValue(value, currency), asPrice = true)
        } else {
            EMPTY_PRICE
        }

    private fun formatAssetAmount(value: BigDecimal): String =
        assetFormat.format(value.stripTrailingZeros())

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

    /**
     * Stops swap quote polling when the form leaves the foreground (navigating into verify/keysign
     * or the app backgrounding), so quotes are not re-fetched in the background — including while
     * the "Transaction failed" screen is shown (#5128).
     */
    fun onScreenPaused() {
        quotePipeline.pause()
    }

    /** Resumes swap quote polling when the form returns to the foreground (#5128). */
    fun onScreenResumed() {
        quotePipeline.resume()
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

        // Snapshot the fee/discount display alongside `inputs`, before launching. Reading
        // _uiState.value inside the coroutine could attach a later quote's fee label or discounts
        // to this transaction if polling lands a new quote in the meantime (#5358).
        val feeDisplay =
            _uiState.value.let { state ->
                SwapFeeDisplay(
                    swapFeePercent = state.feeBreakdown.swapFeePercent,
                    swapFeeIncludedInRate = state.feeBreakdown.swapFeeIncludedInRate,
                    vultBpsDiscount = state.discountInfo.vultBpsDiscount,
                    vultBpsDiscountFiatValue = state.discountInfo.vultBpsDiscountFiatValue,
                    referralBpsDiscount = state.discountInfo.referralBpsDiscount,
                    referralBpsDiscountFiatValue = state.discountInfo.referralBpsDiscountFiatValue,
                )
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
                    feeDisplay = feeDisplay,
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
                // Raise the quote skeletons while loading a not-yet-held token's account only when
                // the pair the pick forms could actually be quoted — a positive amount AND a
                // routable (distinct, provider-backed) pair, mirroring the pipeline's own
                // isPairRoutable && amount>0 gate. A bare amount>0 check would still blink over an
                // unroutable or same-token pair before updatePairSupport catches it (#5296 review).
                isSelectionQuotable = { selectedToken ->
                    isSelectionQuotable(targetArg, selectedToken)
                },
            )
        }
    }

    private fun isSelectionQuotable(targetArg: String, selectedToken: Coin): Boolean {
        val amount = srcAmount ?: return false
        if (amount <= BigDecimal.ZERO) return false
        val (src, dst) =
            when (targetArg) {
                ARG_SELECTED_SRC_TOKEN_ID -> selectedToken to selectedDst.value?.account?.token
                else -> selectedSrc.value?.account?.token to selectedToken
            }
        return src != null && dst != null && quotePipeline.isPairRoutable(src, dst)
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
        // on its own gas chain — a combination the UI no longer offers, since MAX is hidden
        // whenever the source is native (#5317), so this branch is now a guard for direct callers
        // rather than a live path. The provider swap fee is taken from the destination amount (for
        // LI.FI it is denominated in the destination token's units), so it is never deducted from
        // the source balance here — that would mix decimals and could wrongly drive the usable
        // amount negative for a low-decimal source into a high-decimal destination.
        val reservedNetworkFee =
            if (
                percentage >= 1f &&
                    srcToken.isNativeToken &&
                    quotePipeline.gasFeeChain.value == srcToken.chain
            ) {
                val baseFee =
                    quotePipeline.estimatedNetworkFeeTokenValue.value?.value ?: BigInteger.ZERO
                // A Jupiter affiliate swap may create the destination-mint fee ATA on first use,
                // whose ~0.002 SOL rent the payer (this wallet) funds but the network-fee estimate
                // doesn't include. Reserve it on native-SOL MAX so the first such swap can't
                // underfund and fail on submit; it leaves harmless dust when no ATA is created.
                // Jupiter only handles same-chain Solana, so a SOL→non-Solana MAX (routed via
                // THORChain/LI.FI) never creates a fee ATA — don't reserve dust for it.
                val dstChain = selectedDst.value?.account?.token?.chain
                if (srcToken.chain == Chain.Solana && dstChain == Chain.Solana)
                    baseFee + SOLANA_FEE_ATA_RENT_RESERVE
                else baseFee
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
            // launchSingleTop is forced on every navigation, so popping the current swap first is
            // what makes the already-open swap actually re-open with the ETH → VULT pair (#4858).
            navigator.route(
                Route.Swap(
                    vaultId = vaultId,
                    chainId = Chain.Ethereum.id,
                    srcTokenId = Coins.Ethereum.ETH.id,
                    dstTokenId = Coins.Ethereum.VULT.id,
                ),
                NavigationOptions(popUpToRoute = Route.Swap::class, inclusive = true),
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

        // Placeholder shown in the limit form's price fields before a market price resolves.
        private const val EMPTY_PRICE = "—"

        // Rent-exempt minimum for an SPL token account (~0.00203928 SOL, in lamports). Held back on
        // native-SOL MAX swaps to cover a first-use Jupiter fee-ATA creation the fee estimate
        // omits.
        private val SOLANA_FEE_ATA_RENT_RESERVE = BigInteger.valueOf(2_039_280)

        // Grouped, up-to-8-decimal $VULT amount (e.g. "3,000", "6.65648001"); truncates rather than
        // rounds up so a displayed balance never overstates what the vault holds.
        private val VULT_DISPLAY_FORMAT =
            DecimalFormat("#,##0.########").apply { roundingMode = RoundingMode.DOWN }
    }
}
