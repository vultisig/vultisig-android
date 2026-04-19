package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_MANTLE_SWAP_LIMIT
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapTransaction.RegularSwapTransaction
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.usecases.getTierType
import com.vultisig.wallet.data.usecases.resolveprovider.ResolveProviderUseCase
import com.vultisig.wallet.data.usecases.resolveprovider.SwapSelectionContext
import com.vultisig.wallet.ui.models.findCurrentSrc
import com.vultisig.wallet.ui.models.firstSendSrc
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.models.swap.SwapTokenSelector.Companion.ARG_SELECTED_DST_TOKEN_ID
import com.vultisig.wallet.ui.models.swap.SwapTokenSelector.Companion.ARG_SELECTED_SRC_TOKEN_ID
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import timber.log.Timber

internal data class SwapFormUiModel(
    val selectedSrcToken: TokenBalanceUiModel? = null,
    val selectedDstToken: TokenBalanceUiModel? = null,
    val srcFiatValue: String = "0",
    val estimatedDstTokenValue: String = "0",
    val estimatedDstFiatValue: String = "0",
    val provider: UiText = UiText.Empty,
    val networkFee: String = "",
    val networkFeeFiat: String = "",
    val totalFee: String = "0",
    val fee: String = "",
    val error: UiText? = null,
    val formError: UiText? = null,
    val isSwapDisabled: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingNextScreen: Boolean = false,
    val enableMaxAmount: Boolean = false,
    val expiredAt: Instant? = null,
    val tierType: TierType? = null,
    val vultBpsDiscount: Int? = null,
    val vultBpsDiscountFiatValue: String? = null,
    val referralBpsDiscount: Int? = null,
    val referralBpsDiscountFiatValue: String? = null,
)

@HiltViewModel
internal class SwapFormViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val fiatValueToString: FiatValueToStringMapper,
    private val convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase,
    private val resolveProvider: ResolveProviderUseCase,
    private val allowanceRepository: AllowanceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val getDiscountBpsUseCase: GetDiscountBpsUseCase,
    private val referralRepository: ReferralCodeSettingsRepository,
    private val swapValidator: SwapValidator,
    private val swapDiscountChecker: SwapDiscountChecker,
    private val swapGasCalculator: SwapGasCalculator,
    private val swapTokenSelector: SwapTokenSelector,
    private val swapQuoteManager: SwapQuoteManager,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Swap>()

    val uiState = MutableStateFlow(SwapFormUiModel())

    val srcAmountState = TextFieldState()

    private var vaultId: String? = null
    private val chain = MutableStateFlow<Chain?>(null)

    private var quote: SwapQuote? = null

    private var provider: SwapProvider? = null

    private val srcAmount: BigDecimal?
        get() = srcAmountState.text.toString().toBigDecimalOrNull()

    private val selectedSrc = MutableStateFlow<SendSrc?>(null)
    private val selectedDst = MutableStateFlow<SendSrc?>(null)
    private val selectedSrcId = MutableStateFlow<String?>(null)
    private val selectedDstId = MutableStateFlow<String?>(null)
    private val referralCode = MutableStateFlow<String?>(null)

    private val estimatedNetworkFeeTokenValue = MutableStateFlow<TokenValue?>(null)
    private val gasFee = MutableStateFlow<TokenValue?>(null)
    private val swapFeeFiat = MutableStateFlow<FiatValue?>(null)
    private val estimatedNetworkFeeFiatValue = MutableStateFlow<FiatValue?>(null)

    private val addresses = MutableStateFlow<List<Address>>(emptyList())

    private val refreshQuoteState = MutableStateFlow(0)

    private var selectTokensJob: Job? = null

    private var refreshQuoteJob: Job? = null

    private data class PreFlipState(
        val srcAmount: String,
        val srcTokenId: String,
        val dstTokenId: String,
        val flippedAmount: String,
    )

    private var preFlipState: PreFlipState? = null

    private var isLoading: Boolean
        get() = uiState.value.isLoading
        set(value) {
            uiState.update { it.copy(isLoading = value) }
        }

    private var isLoadingNextScreen: Boolean
        get() = uiState.value.isLoadingNextScreen
        set(value) {
            uiState.update { it.copy(isLoadingNextScreen = value) }
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

        swapTokenSelector.collectSelectedAccounts(selectedSrc, selectedDst, uiState, viewModelScope)
        collectSelectedTokens()

        calculateGas()
        calculateFees()
        collectTotalFee()
    }

    fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    fun swap() {
        try {
            isLoadingNextScreen = true
            val vaultId =
                vaultId
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.swap_screen_invalid_no_vault)
                    )
            val selectedSrc =
                selectedSrc.value
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.swap_screen_invalid_no_src_error)
                    )
            val selectedDst =
                selectedDst.value
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.swap_screen_invalid_selected_no_dst)
                    )

            val gasFee =
                gasFee.value
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.swap_screen_invalid_gas_fee_calculation)
                    )
            val gasFeeFiatValue =
                estimatedNetworkFeeFiatValue.value
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.swap_screen_invalid_gas_fee_calculation)
                    )

            val srcToken = selectedSrc.account.token
            val dstToken = selectedDst.account.token

            if (srcToken == dstToken) {
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.swap_screen_same_asset_error_message)
                )
            }

            val srcAddress = selectedSrc.address.address

            val srcAmountInt =
                srcAmount
                    ?.movePointRight(selectedSrc.account.token.decimal)
                    ?.toBigInteger()
                    ?.takeIf { it != BigInteger.ZERO }
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(
                            if (srcAmountState.text.toString().toBigDecimalOrNull() == null)
                                R.string.swap_form_invalid_amount
                            else R.string.swap_screen_invalid_zero_token_amount
                        )
                    )

            val selectedSrcBalance =
                selectedSrc.account.tokenValue?.value
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_insufficient_balance)
                    )

            val srcTokenValue = convertTokenAndValueToTokenValue(srcToken, srcAmountInt)

            val quote =
                quote
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.swap_screen_invalid_quote_calculation)
                    )

            if (srcToken.isNativeToken) {
                if (
                    srcAmountInt + (estimatedNetworkFeeTokenValue.value?.value ?: BigInteger.ZERO) >
                        selectedSrcBalance
                ) {
                    throw InvalidTransactionDataException(
                        UiText.FormattedText(
                            R.string.swap_error_insufficient_balance_and_fees,
                            listOf(srcToken.ticker),
                        )
                    )
                }
            } else {
                val nativeTokenAccount =
                    selectedSrc.address.accounts.find { it.token.isNativeToken }
                val nativeTokenValue =
                    nativeTokenAccount?.tokenValue?.value
                        ?: throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_token)
                        )

                if (selectedSrcBalance < srcAmountInt) {
                    throw InvalidTransactionDataException(
                        UiText.FormattedText(
                            R.string.swap_error_insufficient_source_token,
                            listOf(srcToken.ticker),
                        )
                    )
                }
                if (
                    nativeTokenValue <
                        (estimatedNetworkFeeTokenValue.value?.value ?: BigInteger.ZERO)
                ) {
                    throw InvalidTransactionDataException(
                        UiText.FormattedText(
                            R.string.swap_error_insufficient_gas_fees,
                            listOf(
                                "${nativeTokenAccount.token.ticker} (${nativeTokenAccount.token.chain.raw})"
                            ),
                        )
                    )
                }
            }

            viewModelScope.launch {
                try {
                    val dstTokenValue = quote.expectedDstValue

                    val transaction =
                        when (quote) {
                            is SwapQuote.ThorChain -> {
                                val specificAndUtxo =
                                    swapGasCalculator.getSpecificAndUtxo(
                                        srcToken,
                                        srcAddress,
                                        gasFee,
                                    )

                                val dstAddress =
                                    quote.data.router ?: quote.data.inboundAddress ?: srcAddress
                                val allowance =
                                    allowanceRepository.getAllowance(
                                        chain = srcToken.chain,
                                        contractAddress = srcToken.contractAddress,
                                        srcAddress = srcAddress,
                                        dstAddress = dstAddress,
                                    )
                                val isApprovalRequired =
                                    allowance != null && allowance < srcTokenValue.value

                                val isAffiliate = true

                                RegularSwapTransaction(
                                    id = UUID.randomUUID().toString(),
                                    vaultId = vaultId,
                                    srcToken = srcToken,
                                    srcTokenValue = srcTokenValue,
                                    dstToken = dstToken,
                                    dstAddress = dstAddress,
                                    expectedDstTokenValue = dstTokenValue,
                                    blockChainSpecific = specificAndUtxo,
                                    estimatedFees = quote.fees,
                                    gasFees = estimatedNetworkFeeTokenValue.value ?: gasFee,
                                    isApprovalRequired = isApprovalRequired,
                                    memo = quote.data.memo,
                                    gasFeeFiatValue = gasFeeFiatValue,
                                    payload =
                                        SwapPayload.ThorChain(
                                            THORChainSwapPayload(
                                                fromAddress = srcAddress,
                                                fromCoin = srcToken,
                                                toCoin = dstToken,
                                                vaultAddress =
                                                    quote.data.inboundAddress ?: srcAddress,
                                                routerAddress = quote.data.router,
                                                fromAmount = srcTokenValue.value,
                                                toAmountDecimal = dstTokenValue.decimal,
                                                toAmountLimit = "0",
                                                streamingInterval = "1",
                                                streamingQuantity = "0",
                                                expirationTime =
                                                    (System.currentTimeMillis().milliseconds +
                                                            15.minutes)
                                                        .inWholeSeconds
                                                        .toULong(),
                                                isAffiliate = isAffiliate,
                                            )
                                        ),
                                )
                            }

                            is SwapQuote.MayaChain -> {
                                val specificAndUtxo =
                                    swapGasCalculator.getSpecificAndUtxo(
                                        srcToken,
                                        srcAddress,
                                        gasFee,
                                    )

                                val dstAddress =
                                    if (
                                        !srcToken.isNativeToken &&
                                            srcToken.chain.standard == TokenStandard.EVM
                                    ) {
                                        quote.data.router ?: quote.data.inboundAddress ?: srcAddress
                                    } else {
                                        quote.data.inboundAddress ?: srcAddress
                                    }

                                val allowance =
                                    allowanceRepository.getAllowance(
                                        chain = srcToken.chain,
                                        contractAddress = srcToken.contractAddress,
                                        srcAddress = srcAddress,
                                        dstAddress = dstAddress,
                                    )
                                val isApprovalRequired =
                                    allowance != null && allowance < srcTokenValue.value

                                val isAffiliate = true

                                RegularSwapTransaction(
                                    id = UUID.randomUUID().toString(),
                                    vaultId = vaultId,
                                    srcToken = srcToken,
                                    srcTokenValue = srcTokenValue,
                                    dstToken = dstToken,
                                    dstAddress = dstAddress,
                                    expectedDstTokenValue = dstTokenValue,
                                    blockChainSpecific = specificAndUtxo,
                                    estimatedFees = quote.fees,
                                    gasFees = estimatedNetworkFeeTokenValue.value ?: gasFee,
                                    memo = quote.data.memo,
                                    isApprovalRequired = isApprovalRequired,
                                    gasFeeFiatValue = gasFeeFiatValue,
                                    payload =
                                        SwapPayload.MayaChain(
                                            THORChainSwapPayload(
                                                fromAddress = srcAddress,
                                                fromCoin = srcToken,
                                                toCoin = dstToken,
                                                vaultAddress =
                                                    quote.data.inboundAddress ?: srcAddress,
                                                routerAddress = quote.data.router,
                                                fromAmount = srcTokenValue.value,
                                                toAmountDecimal = dstTokenValue.decimal,
                                                toAmountLimit = "0",
                                                streamingInterval = "3",
                                                streamingQuantity = "0",
                                                expirationTime =
                                                    (System.currentTimeMillis().milliseconds +
                                                            15.minutes)
                                                        .inWholeSeconds
                                                        .toULong(),
                                                isAffiliate = isAffiliate,
                                            )
                                        ),
                                )
                            }

                            is SwapQuote.OneInch -> {
                                val dstAddress = quote.data.tx.to
                                val specificAndUtxo =
                                    swapGasCalculator.getSpecificAndUtxo(
                                        srcToken,
                                        srcAddress,
                                        gasFee,
                                    )

                                val allowance =
                                    allowanceRepository.getAllowance(
                                        chain = srcToken.chain,
                                        contractAddress = srcToken.contractAddress,
                                        srcAddress = srcAddress,
                                        dstAddress = dstAddress,
                                    )
                                val isApprovalRequired =
                                    allowance != null && allowance < srcTokenValue.value

                                val specific = specificAndUtxo.blockChainSpecific
                                val gasLimit =
                                    if (srcToken.chain == Chain.Mantle) {
                                        DEFAULT_MANTLE_SWAP_LIMIT.toLong()
                                    } else {
                                        quote.data.tx.gas
                                    }
                                val quoteData =
                                    if (specific is BlockChainSpecific.Ethereum) {
                                        quote.data.copy(
                                            tx =
                                                quote.data.tx.copy(
                                                    gasPrice = specific.maxFeePerGasWei.toString(),
                                                    gas = gasLimit,
                                                )
                                        )
                                    } else {
                                        quote.data
                                    }

                                RegularSwapTransaction(
                                    id = UUID.randomUUID().toString(),
                                    vaultId = vaultId,
                                    srcToken = srcToken,
                                    srcTokenValue = srcTokenValue,
                                    dstToken = dstToken,
                                    dstAddress = dstAddress,
                                    expectedDstTokenValue = dstTokenValue,
                                    blockChainSpecific = specificAndUtxo,
                                    estimatedFees = quote.fees,
                                    gasFees = estimatedNetworkFeeTokenValue.value ?: gasFee,
                                    memo = null,
                                    isApprovalRequired = isApprovalRequired,
                                    gasFeeFiatValue = gasFeeFiatValue,
                                    payload =
                                        SwapPayload.EVM(
                                            EVMSwapPayloadJson(
                                                fromCoin = srcToken,
                                                toCoin = dstToken,
                                                fromAmount = srcTokenValue.value,
                                                toAmountDecimal = dstTokenValue.decimal,
                                                quote = quoteData,
                                                provider = quote.provider,
                                            )
                                        ),
                                )
                            }
                        }

                    swapTransactionRepository.addTransaction(transaction)

                    navigator.route(
                        Route.VerifySwap(transactionId = transaction.id, vaultId = vaultId)
                    )
                    isLoadingNextScreen = false
                } catch (e: InvalidTransactionDataException) {
                    isLoadingNextScreen = false
                    showError(e.text)
                } catch (e: Exception) {
                    isLoadingNextScreen = false
                    Timber.e(e)
                    showError(UiText.StringResource(R.string.swap_screen_invalid_quote_calculation))
                }
            }
        } catch (e: InvalidTransactionDataException) {
            isLoadingNextScreen = false
            showError(e.text)
            return
        } catch (e: Exception) {
            isLoadingNextScreen = false
            Timber.e(e)
            showError(UiText.StringResource(R.string.swap_screen_invalid_quote_calculation))
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
                uiState = uiState,
            )
        }
    }

    fun flipSelectedTokens() {
        cacheCurrentQuote()

        val currentSrcText = srcAmountState.text.toString()
        val currentSrcTokenId = selectedSrc.value?.account?.token?.id
        val currentDstTokenId = selectedDst.value?.account?.token?.id

        val restoredAmount =
            preFlipState
                ?.takeIf { state ->
                    state.srcTokenId == currentDstTokenId &&
                        state.dstTokenId == currentSrcTokenId &&
                        state.flippedAmount == currentSrcText
                }
                ?.srcAmount

        val newSrcAmount =
            restoredAmount
                ?: quote
                    ?.expectedDstValue
                    ?.decimal
                    ?.formatFlippedAmount(selectedDst.value?.account?.token?.decimal)

        resetQuoteState()

        val bufId = selectedSrcId.value
        selectedSrcId.value = selectedDstId.value
        selectedDstId.value = bufId

        val buffer = selectedSrc.value
        selectedSrc.value = selectedDst.value
        selectedDst.value = buffer

        if (
            newSrcAmount != null &&
                newSrcAmount.toBigDecimalOrNull().let { it != null && it > BigDecimal.ZERO }
        ) {
            srcAmountState.setTextAndPlaceCursorAtEnd(newSrcAmount)
        }

        preFlipState =
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
        val currentQuote = quote ?: return
        val currentProvider = provider ?: return
        val srcToken = selectedSrc.value?.account?.token ?: return
        val dstToken = selectedDst.value?.account?.token ?: return
        val currentAmount = srcAmount?.movePointRight(srcToken.decimal)?.toBigInteger() ?: return

        swapQuoteManager.cacheQuote(
            currentQuote,
            currentProvider,
            srcToken.id,
            dstToken.id,
            currentAmount,
        )
    }

    private fun resetQuoteState() {
        quote = null
        provider = null
        uiState.update {
            it.copy(
                estimatedDstTokenValue = "0",
                estimatedDstFiatValue = "0",
                srcFiatValue = "0",
                formError = null,
            )
        }
    }

    fun selectSrcPercentage(percentage: Float) {
        val selectedSrcAccount = selectedSrc.value?.account ?: return
        val srcTokenValue = selectedSrcAccount.tokenValue ?: return

        val srcToken = selectedSrcAccount.token

        val swapFee = quote?.fees?.value.takeIf { provider == SwapProvider.LIFI } ?: BigInteger.ZERO

        val maxUsableTokenAmount =
            srcTokenValue.value -
                swapFee -
                (estimatedNetworkFeeTokenValue.value?.value?.takeIf { srcToken.isNativeToken }
                    ?: BigInteger.ZERO)

        if (maxUsableTokenAmount <= BigInteger.ZERO) {
            srcAmountState.setTextAndPlaceCursorAtEnd("0")
            return
        }

        val amount =
            TokenValue.createDecimal(maxUsableTokenAmount, srcTokenValue.decimals)
                .multiply(percentage.toBigDecimal())
                .setScale(6, RoundingMode.DOWN)

        srcAmountState.setTextAndPlaceCursorAtEnd(amount.toString())
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
        uiState.update { it.copy(error = errorMessage) }
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
                    gasFee.value = result.gasFee
                    try {
                        estimatedNetworkFeeFiatValue.value = result.estimated.fiatValue
                        estimatedNetworkFeeTokenValue.value = result.estimated.tokenValue

                        uiState.update {
                            it.copy(
                                networkFee = result.estimated.formattedTokenValue,
                                networkFeeFiat = result.estimated.formattedFiatValue,
                            )
                        }
                    } catch (e: Exception) {
                        Timber.e(e)
                        showError(
                            UiText.StringResource(R.string.swap_screen_invalid_gas_fee_calculation)
                        )
                    }
                }
        }
    }

    private fun collectTotalFee() {
        estimatedNetworkFeeFiatValue
            .filterNotNull()
            .combine(swapFeeFiat.filterNotNull()) { gasFeeFiat, swapFeeFiat ->
                gasFeeFiat + swapFeeFiat
            }
            .onEach { totalFee ->
                uiState.update { it.copy(totalFee = fiatValueToString(totalFee)) }
            }
            .launchIn(viewModelScope)
    }

    @OptIn(FlowPreview::class)
    private fun calculateFees() {
        viewModelScope.launch {
            combine(selectedSrc.filterNotNull(), selectedDst.filterNotNull()) { src, dst ->
                    src to dst
                }
                .distinctUntilChanged()
                .combine(srcAmountState.textAsFlow().filter { it.isNotEmpty() }) { address, _ ->
                    address to srcAmount
                }
                .combine(refreshQuoteState) { it, _ -> it }
                .debounce(450L)
                .collect { (address, amount) ->
                    isLoading = true
                    val (src, dst) = address

                    val srcToken = src.account.token
                    val dstToken = dst.account.token

                    val srcTokenValue =
                        amount?.movePointRight(src.account.token.decimal)?.toBigInteger()

                    try {
                        if (srcTokenValue == null || srcTokenValue <= BigInteger.ZERO) {
                            throw SwapException.AmountCannotBeZero("Amount must be positive")
                        }
                        if (srcToken == dstToken) {
                            throw SwapException.SameAssets("Can't swap same assets ${srcToken.id})")
                        }

                        val tokenValue = convertTokenAndValueToTokenValue(srcToken, srcTokenValue)

                        val provider =
                            resolveProvider(SwapSelectionContext(srcToken, dstToken, tokenValue))
                                ?: throw SwapException.SwapIsNotSupported(
                                    "Swap is not supported for this pair"
                                )
                        this@SwapFormViewModel.provider = provider

                        val currency = appCurrencyRepository.currency.first()

                        val vultBPSDiscount =
                            vaultId?.let { id ->
                                getDiscountBpsUseCase.invoke(id, provider).takeIf { it != 0 }
                            }

                        val referral =
                            referralCode.value
                                ?: vaultId?.let { referralRepository.getExternalReferralBy(it) }

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
                                        referralBpsDiscount = result.referralBpsDiscount,
                                        referralBpsDiscountFiatValue =
                                            result.referralBpsDiscountFiatValue,
                                    )
                                }
                            }
                        } else {
                            uiState.update {
                                it.copy(
                                    referralBpsDiscount = null,
                                    referralBpsDiscountFiatValue = null,
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
                                vultBpsDiscount = vultResult.vultBpsDiscount,
                                vultBpsDiscountFiatValue = vultResult.vultBpsDiscountFiatValue,
                                tierType = vultResult.tierType,
                            )
                        }

                        val quoteResult =
                            swapQuoteManager.fetchQuote(
                                provider = provider,
                                src = src,
                                dst = dst,
                                srcToken = srcToken,
                                dstToken = dstToken,
                                srcTokenValue = srcTokenValue,
                                tokenValue = tokenValue,
                                currency = currency,
                                vultBPSDiscount = vultBPSDiscount,
                                referral = referral,
                                amount = amount,
                            )

                        this@SwapFormViewModel.quote = quoteResult.quote
                        swapFeeFiat.value = quoteResult.swapFeeFiat

                        uiState.update {
                            it.copy(
                                provider = quoteResult.providerUiText,
                                srcFiatValue = quoteResult.srcFiatValueText,
                                estimatedDstTokenValue = quoteResult.estimatedDstTokenValue,
                                estimatedDstFiatValue = quoteResult.estimatedDstFiatValue,
                                fee = quoteResult.feeText,
                                formError = null,
                                isSwapDisabled = false,
                                isLoading = false,
                                expiredAt = this@SwapFormViewModel.quote?.expiredAt,
                            )
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
                        this@SwapFormViewModel.quote = null
                        val formError =
                            swapQuoteManager.mapSwapExceptionToFormError(
                                e,
                                srcToken,
                                uiState.value.selectedSrcToken?.title,
                            )
                        uiState.update {
                            it.copy(
                                provider = UiText.Empty,
                                srcFiatValue = "0",
                                estimatedDstTokenValue = "0",
                                estimatedDstFiatValue = "0",
                                fee = "0",
                                isSwapDisabled = true,
                                formError = formError,
                                isLoading = false,
                                expiredAt = null,
                            )
                        }
                        Timber.e(e, "swapError")
                    } catch (e: Exception) {
                        this@SwapFormViewModel.quote = null
                        isLoading = false
                        Timber.e(e)
                    }

                    this@SwapFormViewModel.quote?.expiredAt?.let { launchRefreshQuoteTimer(it) }
                }
        }
    }

    private fun launchRefreshQuoteTimer(expiredAt: Instant) {
        refreshQuoteJob?.cancel()
        refreshQuoteJob =
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    delay(expiredAt - Clock.System.now())
                    refreshQuoteState.value++
                }
            }
    }

    fun hideError() {
        uiState.update { it.copy(error = null, formError = null) }
    }

    private fun showError(error: UiText) {
        uiState.update { it.copy(error = error) }
    }

    companion object {
        const val ETH_GAS_LIMIT: Long = SwapGasCalculator.ETH_GAS_LIMIT
        const val ARB_GAS_LIMIT: Long = SwapGasCalculator.ARB_GAS_LIMIT
    }
}

private const val MAX_DISPLAY_DECIMALS = 8

internal fun BigDecimal.formatFlippedAmount(tokenDecimals: Int? = null): String =
    setScale(
            (tokenDecimals ?: MAX_DISPLAY_DECIMALS).coerceAtMost(MAX_DISPLAY_DECIMALS),
            RoundingMode.DOWN,
        )
        .stripTrailingZeros()
        .toPlainString()

internal fun MutableStateFlow<SendSrc?>.updateSrc(
    selectedTokenId: String?,
    addresses: List<Address>,
    chain: Chain?,
) {
    val selectedSrcValue = value
    value =
        if (addresses.isEmpty()) {
            null
        } else {
            if (selectedSrcValue == null) {
                addresses.firstSendSrc(selectedTokenId, chain)
            } else {
                addresses.findCurrentSrc(selectedTokenId, selectedSrcValue)
            }
        }
}
