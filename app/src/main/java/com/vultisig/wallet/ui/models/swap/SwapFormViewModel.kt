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
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_MANTLE_SWAP_LIMIT
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
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
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.usecases.getTierType
import com.vultisig.wallet.data.utils.safeLaunch
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

internal data class SwapFormUiModel(
    val selectedSrcToken: TokenBalanceUiModel? = null,
    val selectedDstToken: TokenBalanceUiModel? = null,
    val srcFiatValue: String = "0",
    val estimatedDstTokenValue: String = "0",
    val estimatedDstFiatValue: String = "0",
    // True while the shown destination value is an indicative spot-price estimate (rendered greyed)
    // rather than a firm provider quote. Display-only — never gates Continue (#4712).
    val isDstEstimated: Boolean = false,
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
    val hasQuote: Boolean = false,
    val expiredAt: Instant? = null,
    val tierType: TierType? = null,
    val vultBpsDiscount: Int? = null,
    val vultBpsDiscountFiatValue: String? = null,
    val referralBpsDiscount: Int? = null,
    val referralBpsDiscountFiatValue: String? = null,
    val outboundFee: String? = null,
    val swapFeePercent: String? = null,
)

@HiltViewModel
internal class SwapFormViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val fiatValueToString: FiatValueToStringMapper,
    private val convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase,
    private val swapQuoteRepository: SwapQuoteRepository,
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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
    private val gasFeeChain = MutableStateFlow<Chain?>(null)
    private val swapFeeFiat = MutableStateFlow<FiatValue?>(null)
    private val estimatedNetworkFeeFiatValue = MutableStateFlow<FiatValue?>(null)

    private val addresses = MutableStateFlow<List<Address>>(emptyList())

    private val refreshQuoteState = MutableStateFlow(0)

    private var selectTokensJob: Job? = null

    private var refreshQuoteJob: Job? = null

    // Set true right before a programmatic amount change (percentage / Max) so the next amount
    // emission skips the typing debounce and fetches a quote immediately (#4712). Reset as soon as
    // it is consumed by the quote flow so it never leaks into subsequent free typing. Only ever
    // touched on the main thread (UI callbacks + the Main-dispatched quote flow).
    private var fetchQuoteImmediately = false

    // Length of the previous source-amount text, used to distinguish a paste (multi-character jump)
    // from free typing so a paste also fetches immediately (#4712).
    private var lastSrcAmountLength = 0

    private data class PreFlipState(
        val srcAmount: String,
        val srcTokenId: String,
        val dstTokenId: String,
        val flippedAmount: String,
    )

    private data class QuoteInput(
        val address: Pair<SendSrc, SendSrc>,
        val amount: BigDecimal?,
        // True when the change should bypass the typing debounce (percentage / Max / paste).
        val immediate: Boolean,
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
                                val dstAddress =
                                    quote.data.router ?: quote.data.inboundAddress ?: srcAddress
                                val isRouterDeposit =
                                    !srcToken.isNativeToken &&
                                        srcToken.chain.standard == TokenStandard.EVM &&
                                        !quote.data.router.isNullOrEmpty()
                                val specificAndUtxo =
                                    swapGasCalculator.getSpecificAndUtxo(
                                        srcToken = srcToken,
                                        srcAddress = srcAddress,
                                        gasFee = gasFee,
                                        isThorchainRouterDeposit = isRouterDeposit,
                                        dstAddress = if (isRouterDeposit) dstAddress else null,
                                        memo = if (isRouterDeposit) quote.data.memo else null,
                                        tokenAmountValue =
                                            if (isRouterDeposit) srcTokenValue.value else null,
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
                                    gasFeeFiatValue =
                                        estimatedNetworkFeeFiatValue.value ?: gasFeeFiatValue,
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
                                val isRouterDeposit =
                                    !srcToken.isNativeToken &&
                                        srcToken.chain.standard == TokenStandard.EVM &&
                                        !quote.data.router.isNullOrEmpty()
                                val dstAddress =
                                    if (
                                        !srcToken.isNativeToken &&
                                            srcToken.chain.standard == TokenStandard.EVM
                                    ) {
                                        quote.data.router ?: quote.data.inboundAddress ?: srcAddress
                                    } else {
                                        quote.data.inboundAddress ?: srcAddress
                                    }
                                val specificAndUtxo =
                                    swapGasCalculator.getSpecificAndUtxo(
                                        srcToken = srcToken,
                                        srcAddress = srcAddress,
                                        gasFee = gasFee,
                                        isThorchainRouterDeposit = isRouterDeposit,
                                        dstAddress = if (isRouterDeposit) dstAddress else null,
                                        memo = if (isRouterDeposit) quote.data.memo else null,
                                        tokenAmountValue =
                                            if (isRouterDeposit) srcTokenValue.value else null,
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
                                    gasFeeFiatValue =
                                        estimatedNetworkFeeFiatValue.value ?: gasFeeFiatValue,
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

                            is SwapQuote.SwapKit -> {
                                // BTC PSBT, TRON (TronWeb object), SUI (PTB), TON (native
                                // transfer), XRP (deposit-only native Payment), and both Cardano
                                // flows (deposit-only CARDANO + pre-built CARDANO_PREBUILT) are
                                // wired with their per-chain signers/native paths. Guarded loudly
                                // so an un-wired txType can't reach signing.
                                require(
                                    quote.data.txType == SwapKitSwapPayloadJson.TX_TYPE_PSBT ||
                                        quote.data.txType == SwapKitSwapPayloadJson.TX_TYPE_TRON ||
                                        quote.data.txType == SwapKitSwapPayloadJson.TX_TYPE_SUI ||
                                        quote.data.txType == SwapKitSwapPayloadJson.TX_TYPE_TON ||
                                        quote.data.txType == SwapKitSwapPayloadJson.TX_TYPE_XRP ||
                                        quote.data.txType ==
                                            SwapKitSwapPayloadJson.TX_TYPE_CARDANO ||
                                        quote.data.txType ==
                                            SwapKitSwapPayloadJson.TX_TYPE_CARDANO_PREBUILT
                                ) {
                                    "Unsupported SwapKit txType for swap: ${quote.data.txType}"
                                }
                                val specificAndUtxo =
                                    swapGasCalculator.getSpecificAndUtxo(
                                        srcToken = srcToken,
                                        srcAddress = srcAddress,
                                        gasFee = gasFee,
                                    )
                                RegularSwapTransaction(
                                    id = UUID.randomUUID().toString(),
                                    vaultId = vaultId,
                                    srcToken = srcToken,
                                    srcTokenValue = srcTokenValue,
                                    dstToken = dstToken,
                                    // SwapKit's source-chain deposit address. Signing is driven
                                    // entirely by the payload bytes (PSBT / TronWeb object), not by
                                    // this blockChainSpecific.
                                    dstAddress = quote.data.targetAddress,
                                    expectedDstTokenValue = dstTokenValue,
                                    blockChainSpecific = specificAndUtxo,
                                    estimatedFees = quote.fees,
                                    gasFees = estimatedNetworkFeeTokenValue.value ?: gasFee,
                                    memo = quote.data.memo,
                                    isApprovalRequired = false,
                                    gasFeeFiatValue =
                                        estimatedNetworkFeeFiatValue.value ?: gasFeeFiatValue,
                                    payload = SwapPayload.SwapKit(quote.data),
                                )
                            }

                            is SwapQuote.OneInch -> {
                                val dstAddress = quote.data.tx.to
                                // The ERC20 allowance must be granted to the provider's token-
                                // transfer proxy, which for SwapKit differs from the swap `to`.
                                // Derivation is factored into approveSpenderFor (pinned by test) so
                                // a regression collapsing it to `to` can't pass CI silently.
                                val approveSpender = approveSpenderFor(quote.data.tx)
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
                                        dstAddress = approveSpender,
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
                                    approveSpender = approveSpender,
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
                    if (e is kotlinx.coroutines.CancellationException) throw e
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

        val swapFee = quote?.fees?.value.takeIf { provider == SwapProvider.LIFI } ?: BigInteger.ZERO

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
        // instead of waiting out the typing debounce (#4712). Set the flag before mutating the text
        // so the resulting emission is already marked immediate.
        fetchQuoteImmediately = true
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
                                    networkFee = result.estimated.formattedTokenValue,
                                    networkFeeFiat = result.estimated.formattedFiatValue,
                                )
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
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
                        uiState.update { it.copy(networkFee = "", networkFeeFiat = "") }
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
            .combine(swapFeeFiat.filterNotNull()) { gasFeeFiat, swapFeeFiat ->
                gasFeeFiat + swapFeeFiat
            }
            .onEach { totalFee ->
                uiState.update { it.copy(totalFee = fiatValueToString(totalFee, asFee = true)) }
            }
            .launchIn(viewModelScope)
    }

    @OptIn(FlowPreview::class)
    private fun calculateFees() {
        viewModelScope.safeLaunch {
            // Emits once per source-amount change carrying whether the quote should fetch
            // immediately (percentage / Max / paste) instead of waiting out the typing debounce.
            val amountChanges =
                srcAmountState
                    .textAsFlow()
                    .map { it.toString() }
                    .map { text ->
                        // A multi-character jump is a paste; length is tracked across empties so a
                        // clear-then-paste is still detected.
                        val isPaste = text.length - lastSrcAmountLength > 1
                        lastSrcAmountLength = text.length
                        text to isPaste
                    }
                    .map { (text, isPaste) ->
                        // Empty input (field cleared) must still flow through so collectLatest hits
                        // the zero-amount branch and resetQuoteState() clears the stale quote —
                        // dropping it here would leave the previous quote on screen. It rides the
                        // normal (non-immediate) path; there is nothing to fetch, so we also leave
                        // fetchQuoteImmediately for the next real amount (#4712).
                        if (text.isEmpty()) return@map false
                        val immediate = fetchQuoteImmediately || isPaste
                        fetchQuoteImmediately = false
                        immediate
                    }

            combine(selectedSrc.filterNotNull(), selectedDst.filterNotNull()) { src, dst ->
                    src to dst
                }
                .distinctUntilChanged()
                .onEach {
                    // A freshly selected pair has no quote yet, and a token switch never clears the
                    // previous pair's destination value. Reset it so the skeleton shows while the
                    // new quote loads instead of the stale amount reading as a firm quote for the
                    // new pair (#4712 review).
                    uiState.update { it.copy(estimatedDstTokenValue = "0", isDstEstimated = false) }
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
                    isLoading = true
                    showIndicativeRate(input)
                }
                .combine(refreshQuoteState) { input, _ -> input }
                // Percentage / Max / paste skip the debounce (0ms); free typing still coalesces at
                // 300ms so rapid edits fire a single quote fetch.
                .debounce { input -> if (input.immediate) 0L else QUOTE_DEBOUNCE_MS }
                // collectLatest so newer input cancels an in-flight fetch instead of letting a
                // stale fetch write isLoading = false after the user has already typed again.
                .collectLatest { input ->
                    val (src, dst) = input.address
                    val amount = input.amount

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
                                ?: vaultId?.let { referralRepository.getExternalReferralBy(it) }

                        val candidates = coroutineScope {
                            eligibleProviders
                                .map { p ->
                                    async {
                                        val discount =
                                            vaultId?.let { id ->
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

                        val bestQuote =
                            swapQuoteManager.fetchBestQuote(
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

                        val quoteResult = bestQuote.result
                        val provider = quoteResult.provider
                        this@SwapFormViewModel.provider = provider

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

                        this@SwapFormViewModel.quote = quoteResult.quote
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
                                provider = quoteResult.providerUiText,
                                srcFiatValue = quoteResult.srcFiatValueText,
                                estimatedDstTokenValue = quoteResult.estimatedDstTokenValue,
                                estimatedDstFiatValue = quoteResult.estimatedDstFiatValue,
                                isDstEstimated = false,
                                fee = feeText,
                                outboundFee = quoteResult.outboundFeeText,
                                swapFeePercent = quoteResult.swapFeePercent,
                                formError = null,
                                isSwapDisabled = isUtxoSwap,
                                isLoading = false,
                                hasQuote = true,
                                expiredAt = this@SwapFormViewModel.quote?.expiredAt,
                            )
                        }

                        if (isUtxoSwap) {
                            val currentGasFee =
                                gasFee.value?.takeIf { gasFeeChain.value == srcToken.chain }
                            val currentVaultId = vaultId
                            if (currentGasFee != null && currentVaultId != null) {
                                val (utxoDstAddress, utxoMemo) = utxoFeeData!!
                                try {
                                    val specificAndUtxo =
                                        swapGasCalculator.getSpecificAndUtxo(
                                            srcToken,
                                            src.address.address,
                                            currentGasFee,
                                        )
                                    val planFeeResult =
                                        swapGasCalculator.computeUtxoPlanFeeResult(
                                            vaultId = currentVaultId,
                                            srcToken = srcToken,
                                            dstAddress = utxoDstAddress,
                                            tokenAmountInt = srcTokenValue,
                                            specificAndUtxo = specificAndUtxo,
                                            memo = utxoMemo,
                                        )
                                    if (planFeeResult != null) {
                                        estimatedNetworkFeeFiatValue.value =
                                            planFeeResult.estimated.fiatValue
                                        estimatedNetworkFeeTokenValue.value =
                                            planFeeResult.estimated.tokenValue
                                        uiState.update {
                                            it.copy(
                                                networkFee =
                                                    planFeeResult.estimated.formattedTokenValue,
                                                networkFeeFiat =
                                                    planFeeResult.estimated.formattedFiatValue,
                                                isSwapDisabled = false,
                                            )
                                        }
                                    } else {
                                        estimatedNetworkFeeTokenValue.value = null
                                        estimatedNetworkFeeFiatValue.value = null
                                        uiState.update {
                                            it.copy(
                                                isSwapDisabled = true,
                                                networkFee = "",
                                                networkFeeFiat = "",
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlin.coroutines.cancellation.CancellationException)
                                        throw e
                                    if (e is InsufficientUtxosException) {
                                        uiState.update {
                                            it.copy(
                                                isSwapDisabled = true,
                                                formError =
                                                    UiText.StringResource(
                                                        R.string.insufficient_utxos_error
                                                    ),
                                            )
                                        }
                                    } else {
                                        estimatedNetworkFeeTokenValue.value = null
                                        estimatedNetworkFeeFiatValue.value = null
                                        uiState.update {
                                            it.copy(
                                                isSwapDisabled = true,
                                                networkFee = "",
                                                networkFeeFiat = "",
                                            )
                                        }
                                    }
                                    Timber.e(e, "utxoPlanFee")
                                }
                            } else {
                                // gasFeeChain lags srcToken.chain after a token switch:
                                // clear any stale fee from the previous chain.
                                estimatedNetworkFeeTokenValue.value = null
                                estimatedNetworkFeeFiatValue.value = null
                                uiState.update { it.copy(networkFee = "", networkFeeFiat = "") }
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
                                    uiState.value.selectedSrcToken?.title,
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

                    this@SwapFormViewModel.quote?.expiredAt?.let { launchRefreshQuoteTimer(it) }
                }
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
                    estimatedDstTokenValue = indicative.estimatedDstTokenValue,
                    estimatedDstFiatValue = indicative.estimatedDstFiatValue,
                    isDstEstimated = true,
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

    private fun resetQuoteState(error: UiText?, cause: Throwable?, tag: String?) {
        // The prior quote's refresh timer would otherwise fire mid-flip/mid-error and re-run
        // calculateFees against the same invalid amount, briefly re-exposing the fee block.
        refreshQuoteJob?.cancel()
        refreshQuoteJob = null
        this@SwapFormViewModel.quote = null
        this@SwapFormViewModel.provider = null
        // collectTotalFee() combines this with estimatedNetworkFeeFiatValue. Resetting it
        // to null lets filterNotNull() short-circuit so a later calculateGas() update can't
        // write a (newGas + staleSwap) combination back into state.totalFee — the same race
        // that triggers on flipSelectedTokens since selectedSrc changes synchronously.
        swapFeeFiat.value = null
        // networkFee/networkFeeFiat are tied to the source token (calculateGas), not to a
        // specific quote, so we deliberately leave them alone — resetting them would leave
        // them empty until selectedSrc changes again.
        uiState.update {
            it.copy(
                provider = UiText.Empty,
                srcFiatValue = "0",
                estimatedDstTokenValue = "0",
                estimatedDstFiatValue = "0",
                isDstEstimated = false,
                fee = "0",
                totalFee = "0",
                vultBpsDiscount = null,
                vultBpsDiscountFiatValue = null,
                referralBpsDiscount = null,
                referralBpsDiscountFiatValue = null,
                outboundFee = null,
                swapFeePercent = null,
                tierType = null,
                isSwapDisabled = true,
                hasQuote = false,
                formError = error,
                isLoading = false,
                expiredAt = null,
            )
        }
        if (cause != null) {
            Timber.e(cause, tag)
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
        // Coalesces rapid free typing into a single quote fetch; bypassed (0ms) for
        // percentage / Max / paste.
        private const val QUOTE_DEBOUNCE_MS = 300L
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
