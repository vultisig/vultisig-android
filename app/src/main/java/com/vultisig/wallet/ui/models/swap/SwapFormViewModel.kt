@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.tx
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_MANTLE_SWAP_LIMIT
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.IsSwapSupported
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapQuote.Companion.expiredAfter
import com.vultisig.wallet.data.models.SwapTransaction.RegularSwapTransaction
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KyberSwapPayloadJson
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.ConvertTokenToToken
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.usecases.SearchTokenUseCase
import com.vultisig.wallet.data.usecases.resolveprovider.ResolveProviderUseCase
import com.vultisig.wallet.data.usecases.resolveprovider.SwapSelectionContext
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.select.AssetSelected
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
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
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import kotlin.minus
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
    val isSwapDisabled: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingNextScreen: Boolean = false,
    val expiredAt: Instant? = null,
)


@HiltViewModel
internal class SwapFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToString: FiatValueToStringMapper,
    private val convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase,
    private val resolveProvider: ResolveProviderUseCase,

    private val allowanceRepository: AllowanceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val accountsRepository: AccountsRepository,
    private val gasFeeRepository: GasFeeRepository,
    private val swapQuoteRepository: SwapQuoteRepository,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val tokenRepository: TokenRepository,
    private val requestResultRepository: RequestResultRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val searchToken: SearchTokenUseCase,
    private val referralRepository: ReferralCodeSettingsRepository,
    private val convertTokenToTokenUseCase: ConvertTokenToToken,
    private val getDiscountBpsUseCase: GetDiscountBpsUseCase,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Swap>()

    val uiState = MutableStateFlow(SwapFormUiModel())

    val srcAmountState = TextFieldState()

    private var vaultId: String? = null
    private var chain: Chain? = null

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

    private var isLoading: Boolean
        get() = uiState.value.isLoading
        set(value) {
            uiState.update {
                it.copy(isLoading = value)
            }
        }

    private var isLoadingNextScreen: Boolean
        get() = uiState.value.isLoadingNextScreen
        set(value) {
            uiState.update {
                it.copy(isLoadingNextScreen = value)
            }
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

        collectSelectedAccounts()
        collectSelectedTokens()

        calculateGas()
        calculateFees()
        collectTotalFee()
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun swap() {
        try {
            // TODO verify swap info
            isLoadingNextScreen = true
            val vaultId = vaultId ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.swap_screen_invalid_no_vault)
            )
            val selectedSrc = selectedSrc.value ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.swap_screen_invalid_no_src_error)
            )
            val selectedDst = selectedDst.value ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.swap_screen_invalid_selected_no_dst)
            )

            val gasFee = gasFee.value ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.swap_screen_invalid_gas_fee_calculation)
            )
            val gasFeeFiatValue =
                estimatedNetworkFeeFiatValue.value ?: throw InvalidTransactionDataException(
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
                srcAmount?.movePointRight(selectedSrc.account.token.decimal)?.toBigInteger()
                    ?.takeIf { it != BigInteger.ZERO } ?: throw InvalidTransactionDataException(
                    UiText.StringResource(
                        if (srcAmountState.text.toString()
                                .toBigDecimalOrNull() == null
                        ) R.string.swap_form_invalid_amount
                        else R.string.swap_screen_invalid_zero_token_amount
                    )
                )

            val selectedSrcBalance =
                selectedSrc.account.tokenValue?.value ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_insufficient_balance)
                )

            val srcTokenValue = convertTokenAndValueToTokenValue(srcToken, srcAmountInt)

            val quote = quote ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.swap_screen_invalid_quote_calculation)
            )

            if (srcToken.isNativeToken) {
                if (srcAmountInt + (estimatedNetworkFeeTokenValue.value?.value
                        ?: BigInteger.ZERO) > selectedSrcBalance
                ) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_insufficient_balance)
                    )
                }
            } else {
                val nativeTokenAccount =
                    selectedSrc.address.accounts.find { it.token.isNativeToken }
                val nativeTokenValue =
                    nativeTokenAccount?.tokenValue?.value ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_token)
                    )

                if (selectedSrcBalance < srcAmountInt) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_insufficient_balance)
                    )
                }
                if (nativeTokenValue < (estimatedNetworkFeeTokenValue.value?.value
                        ?: BigInteger.ZERO)
                ) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.signing_error_insufficient_funds)
                    )
                }
            }

            viewModelScope.launch {
                try {
                    val dstTokenValue = quote.expectedDstValue

                    val transaction = when (quote) {
                        is SwapQuote.ThorChain -> {
                            val specificAndUtxo = getSpecificAndUtxo(srcToken, srcAddress, gasFee)

                            val dstAddress =
                                quote.data.router ?: quote.data.inboundAddress ?: srcAddress
                            val allowance = allowanceRepository.getAllowance(
                                chain = srcToken.chain,
                                contractAddress = srcToken.contractAddress,
                                srcAddress = srcAddress,
                                dstAddress = dstAddress,
                            )
                            val isApprovalRequired =
                                allowance != null && allowance < srcTokenValue.value

                            val srcFiatValue = convertTokenValueToFiat(
                                srcToken, srcTokenValue, AppCurrency.USD,
                            )

                            val isAffiliate =
                                srcFiatValue.value >= AFFILIATE_FEE_USD_THRESHOLD.toBigDecimal()

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
                                payload = SwapPayload.ThorChain(
                                    THORChainSwapPayload(
                                        fromAddress = srcAddress,
                                        fromCoin = srcToken,
                                        toCoin = dstToken,
                                        vaultAddress = quote.data.inboundAddress ?: srcAddress,
                                        routerAddress = quote.data.router,
                                        fromAmount = srcTokenValue.value,
                                        toAmountDecimal = dstTokenValue.decimal,
                                        toAmountLimit = "0",
                                        streamingInterval = "1",
                                        streamingQuantity = "0",
                                        expirationTime = (System.currentTimeMillis().milliseconds + 15.minutes).inWholeSeconds.toULong(),
                                        isAffiliate = isAffiliate,
                                    )
                                )
                            )
                        }

                        is SwapQuote.MayaChain -> {
                            val specificAndUtxo = getSpecificAndUtxo(srcToken, srcAddress, gasFee)

                            val dstAddress =
                                if (!srcToken.isNativeToken && srcToken.chain.standard == TokenStandard.EVM) {
                                    quote.data.router ?: quote.data.inboundAddress ?: srcAddress
                                } else {
                                    quote.data.inboundAddress ?: srcAddress
                                }

                            val allowance = allowanceRepository.getAllowance(
                                chain = srcToken.chain,
                                contractAddress = srcToken.contractAddress,
                                srcAddress = srcAddress,
                                dstAddress = dstAddress,
                            )
                            val isApprovalRequired =
                                allowance != null && allowance < srcTokenValue.value

                            val srcFiatValue = convertTokenValueToFiat(
                                srcToken, srcTokenValue, AppCurrency.USD,
                            )

                            val isAffiliate =
                                srcFiatValue.value >= AFFILIATE_FEE_USD_THRESHOLD.toBigDecimal()

                            val regularSwapTransaction = RegularSwapTransaction(
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
                                payload = SwapPayload.MayaChain(
                                    THORChainSwapPayload(
                                        fromAddress = srcAddress,
                                        fromCoin = srcToken,
                                        toCoin = dstToken,
                                        vaultAddress = quote.data.inboundAddress ?: srcAddress,
                                        routerAddress = quote.data.router,
                                        fromAmount = srcTokenValue.value,
                                        toAmountDecimal = dstTokenValue.decimal,
                                        toAmountLimit = "0",
                                        streamingInterval = "3",
                                        streamingQuantity = "0",
                                        expirationTime = (System.currentTimeMillis().milliseconds + 15.minutes).inWholeSeconds.toULong(),
                                        isAffiliate = isAffiliate,
                                    )
                                )
                            )

                            regularSwapTransaction
                        }

                        is SwapQuote.Kyber -> {
                            val dstAddress = quote.data.tx.to
                            val specificAndUtxo = getSpecificAndUtxo(srcToken, srcAddress, gasFee)

                            val allowance = allowanceRepository.getAllowance(
                                chain = srcToken.chain,
                                contractAddress = srcToken.contractAddress,
                                srcAddress = srcAddress,
                                dstAddress = dstAddress,
                            )
                            val isApprovalRequired =
                                allowance != null && allowance < srcTokenValue.value

                            val specific = specificAndUtxo.blockChainSpecific
                            val quoteData = if (specific is BlockChainSpecific.Ethereum) {
                                quote.data.copy(
                                    data = quote.data.data.copy(gasPrice = specific.maxFeePerGasWei.toString())
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
                                payload = SwapPayload.Kyber(
                                    KyberSwapPayloadJson(
                                        fromCoin = srcToken,
                                        toCoin = dstToken,
                                        fromAmount = srcTokenValue.value,
                                        toAmountDecimal = dstTokenValue.decimal,
                                        quote = quoteData,
                                    )
                                )
                            )
                        }

                        is SwapQuote.OneInch -> {
                            val dstAddress = quote.data.tx.to
                            val specificAndUtxo = getSpecificAndUtxo(srcToken, srcAddress, gasFee)

                            val allowance = allowanceRepository.getAllowance(
                                chain = srcToken.chain,
                                contractAddress = srcToken.contractAddress,
                                srcAddress = srcAddress,
                                dstAddress = dstAddress,
                            )
                            val isApprovalRequired =
                                allowance != null && allowance < srcTokenValue.value

                            val specific = specificAndUtxo.blockChainSpecific
                            val gasLimit = if (srcToken.chain == Chain.Mantle) {
                                DEFAULT_MANTLE_SWAP_LIMIT.toLong()
                            } else {
                                quote.data.tx.gas
                            }
                            val quoteData = if (specific is BlockChainSpecific.Ethereum) {
                                quote.data.copy(
                                    tx = quote.data.tx.copy(
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
                                payload = SwapPayload.EVM(
                                    EVMSwapPayloadJson(
                                        fromCoin = srcToken,
                                        toCoin = dstToken,
                                        fromAmount = srcTokenValue.value,
                                        toAmountDecimal = dstTokenValue.decimal,
                                        quote = quoteData,
                                        provider = quote.provider,
                                    ),
                                )
                            )
                        }
                    }

                    swapTransactionRepository.addTransaction(transaction)

                    navigator.route(
                        Route.VerifySwap(
                            transactionId = transaction.id,
                            vaultId = vaultId,
                        )
                    )
                    isLoadingNextScreen = false
                }
                catch (e: InvalidTransactionDataException) {
                    isLoadingNextScreen = false
                    showError(e.text)
                }  catch (e: Exception) {
                    isLoadingNextScreen = false
                    Timber.e(e)
                    showError(
                        UiText.StringResource(R.string.swap_screen_invalid_quote_calculation)
                    )
                }
            }
        } catch (e: InvalidTransactionDataException) {
            isLoadingNextScreen = false
            showError(e.text)
            return
        }
        catch (e: Exception) {
            isLoadingNextScreen = false
            Timber.e(e)
            showError(
                UiText.StringResource(R.string.swap_screen_invalid_quote_calculation)
            )
        }
    }

    private suspend fun getSpecificAndUtxo(
        srcToken: Coin,
        srcAddress: String,
        gasFee: TokenValue,
    ) = try {
        blockChainSpecificRepository.getSpecific(
            chain = srcToken.chain,
            address = srcAddress,
            token = srcToken,
            gasFee = gasFee,
            isSwap = true,
            isMaxAmountEnabled = false,
            isDeposit = srcToken.chain == Chain.MayaChain,
            gasLimit = getGasLimit(srcToken),
        )
    } catch (e: Exception) {
        Timber.d(e)
        throw InvalidTransactionDataException(
            UiText.StringResource(R.string.swap_screen_invalid_specific_and_utxo)
        )
    }

    fun selectSrcNetwork() {
        viewModelScope.launch {
            val newSendSrc = selectNetwork(
                vaultId = vaultId ?: return@launch,
                selectedChain = selectedSrc.value?.address?.chain ?: return@launch,
            ) ?: return@launch

            selectedSrcId.value = newSendSrc.account.token.id
        }
    }

    fun selectDstNetwork() {
        viewModelScope.launch {
            val newSendSrc = selectNetwork(
                vaultId = vaultId ?: return@launch,
                selectedChain = selectedDst.value?.address?.chain ?: return@launch,
            ) ?: return@launch

            selectedDstId.value = newSendSrc.account.token.id
        }
    }

    private suspend fun selectNetwork(
        vaultId: VaultId,
        selectedChain: Chain,
    ): SendSrc? {
        val requestId = Uuid.random().toString()
        navigator.route(
            Route.SelectNetwork(
                vaultId = vaultId,
                selectedNetworkId = selectedChain.id,
                requestId = requestId,
                filters = Route.SelectNetwork.Filters.SwapAvailable,
            )
        )

        val chain: Chain = requestResultRepository.request(requestId) ?: return null

        if (chain == selectedChain) {
            return null
        }

        return addresses.value.firstSendSrc(
            selectedTokenId = null,
            filterByChain = chain,
        )
    }

    fun selectSrcToken() {
        navigateToSelectToken(ARG_SELECTED_SRC_TOKEN_ID)
    }

    fun selectDstToken() {
        navigateToSelectToken(ARG_SELECTED_DST_TOKEN_ID)
    }

    private fun navigateToSelectToken(
        targetArg: String,
    ) {
        viewModelScope.launch {
            navigator.route(
                Route.SelectAsset(
                    vaultId = vaultId ?: return@launch,
                    requestId = targetArg,
                    preselectedNetworkId = (when (targetArg) {
                        ARG_SELECTED_SRC_TOKEN_ID -> selectedSrc.value?.address?.chain
                        ARG_SELECTED_DST_TOKEN_ID -> selectedDst.value?.address?.chain
                        else -> Chain.ThorChain
                    })?.id ?: Chain.ThorChain.id,
                    networkFilters = Route.SelectNetwork.Filters.SwapAvailable,
                )
            )
            checkTokenSelectionResponse(targetArg)
        }
    }

    private suspend fun checkTokenSelectionResponse(targetArg: String) {
        val result = requestResultRepository.request<AssetSelected>(targetArg) ?: return

        if (result.isDisabled) {
            uiState.update { it.copy(isLoading = true) }
            vaultId?.let {
                try {
                    val account = accountsRepository.loadAccount(vaultId!!, result.token)
                    updateAccountInAddresses(account)
                    uiState.update { it.copy(isLoading = false) }
                } catch (t: Throwable) {
                    uiState.update { it.copy(isLoading = false) }
                    return
                }
            } ?: run {
                uiState.update { it.copy(isLoading = false) }
            }
        }

        if (targetArg == ARG_SELECTED_SRC_TOKEN_ID) {
            selectedSrcId.value = result.token.id
        } else {
            selectedDstId.value = result.token.id
        }
    }

    private fun updateAccountInAddresses(
        loadedAccount: Account
    ) {
        addresses.update { listOfAddreses ->
            listOfAddreses.map { address ->
                if (address.chain == loadedAccount.token.chain) {
                    address.copy(accounts = address.accounts + listOf(loadedAccount))
                } else {
                    address
                }
            }
        }
    }

    fun flipSelectedTokens() {
        viewModelScope.launch {
            val buffer = selectedSrc.value
            selectedSrc.value = selectedDst.value
            selectedDst.value = buffer
        }
    }

    fun selectSrcPercentage(percentage: Float) {
        val selectedSrcAccount = selectedSrc.value?.account ?: return
        val srcTokenValue = selectedSrcAccount.tokenValue ?: return

        val srcToken = selectedSrcAccount.token

        val swapFee = quote?.fees?.value.takeIf { provider == SwapProvider.LIFI } ?: BigInteger.ZERO

        val maxUsableTokenAmount =
            srcTokenValue.value - swapFee - (estimatedNetworkFeeTokenValue.value?.value?.takeIf { srcToken.isNativeToken }
                ?: BigInteger.ZERO)

        if (maxUsableTokenAmount <= BigInteger.ZERO) {
            srcAmountState.setTextAndPlaceCursorAtEnd("0")
            return
        }

        val amount = TokenValue.createDecimal(maxUsableTokenAmount, srcTokenValue.decimals)
            .multiply(percentage.toBigDecimal()).setScale(6, RoundingMode.DOWN)

        srcAmountState.setTextAndPlaceCursorAtEnd(amount.toString())
    }

    fun loadData(
        vaultId: String,
        chainId: String?,
        srcTokenId: String?,
        dstTokenId: String?,
    ) {
        this.chain = chainId?.let(Chain::fromRaw)

        if (srcTokenId != null && this.selectedSrcId.value == null) {
            selectedSrcId.value = srcTokenId
        }

        if (dstTokenId != null && this.selectedDstId.value == null) {
            selectedDstId.value = dstTokenId
        }

        if (this.vaultId != vaultId) {
            this.vaultId = vaultId
            loadTokens(vaultId)
        }
    }

    fun validateAmount() {
        val errorMessage = validateSrcAmount(srcAmountState.text.toString())
        uiState.update { it.copy(error = errorMessage) }
    }

    private fun loadTokens(
        vaultId: String,
    ) {
        viewModelScope.launch {
            accountsRepository.loadAddresses(vaultId).map { addresses ->
                    addresses.filter { it.chain.IsSwapSupported }
                }.catch {
                    Timber.e(it)
                    emit(emptyList())
                }.collect(addresses)
        }
    }

    private fun collectSelectedTokens() {
        selectTokensJob?.cancel()
        selectTokensJob = viewModelScope.launch {
            combine(
                addresses.filter { it.isNotEmpty() }, // Only proceed when addresses loaded
                selectedSrcId,
                selectedDstId,
            ) { addresses, srcTokenId, dstTokenId ->
                val chain = chain
                selectedSrc.updateSrc(srcTokenId, addresses, chain)
                selectedDst.updateSrc(dstTokenId, addresses, chain)
            }.collect()
        }
    }

    private fun collectSelectedAccounts() {
        viewModelScope.launch {
            combine(
                selectedSrc,
                selectedDst,
            ) { src, dst ->
                val srcUiModel = src?.let { accountToTokenBalanceUiModelMapper(it) }
                val dstUiModel = dst?.let { accountToTokenBalanceUiModelMapper(it) }

                uiState.update {
                    it.copy(
                        selectedSrcToken = srcUiModel,
                        selectedDstToken = dstUiModel,
                    )
                }
            }.collect()
        }
    }

    private fun calculateGas() {
        viewModelScope.launch {
            selectedSrc.filterNotNull().map {
                    it to gasFeeRepository.getGasFee(it.address.chain, it.address.address, true)
                }.catch {
                    // TODO handle error when querying gas fee
                    Timber.e(it)
                }.collect { (selectedSrc, gasFee) ->
                    this@SwapFormViewModel.gasFee.value = gasFee
                    val selectedAccount = selectedSrc.account
                    val chain = selectedAccount.token.chain
                    val selectedToken = selectedAccount.token
                    val srcAddress = selectedAccount.token.address
                    try {
                        val spec = getSpecificAndUtxo(selectedToken, srcAddress, gasFee)

                        val estimatedNetworkFee = gasFeeToEstimatedFee(
                            GasFeeParams(
                                gasLimit = if (chain.standard == TokenStandard.EVM) {
                                    (spec.blockChainSpecific as BlockChainSpecific.Ethereum).gasLimit
                                } else {
                                    BigInteger.valueOf(1)
                                },
                                gasFee = gasFee,
                                selectedToken = selectedToken,
                            )
                        )

                        estimatedNetworkFeeFiatValue.value = estimatedNetworkFee.fiatValue
                        estimatedNetworkFeeTokenValue.value = estimatedNetworkFee.tokenValue

                        uiState.update {
                            it.copy(
                                networkFee = estimatedNetworkFee.formattedTokenValue,
                                networkFeeFiat = estimatedNetworkFee.formattedFiatValue,
                            )
                        }
                    } catch (e: Exception) {
                        Timber.e(e)
                        showError(UiText.StringResource(R.string.swap_screen_invalid_gas_fee_calculation))
                    }
                }
        }
    }

    private fun collectTotalFee() {
        estimatedNetworkFeeFiatValue.filterNotNull()
            .combine(swapFeeFiat.filterNotNull()) { gasFeeFiat, swapFeeFiat ->
                gasFeeFiat + swapFeeFiat
            }.onEach { totalFee ->
                uiState.update {
                    it.copy(totalFee = fiatValueToString(totalFee))
                }
            }.launchIn(viewModelScope)
    }

    @OptIn(FlowPreview::class)
    private fun calculateFees() {
        viewModelScope.launch {
            combine(
                selectedSrc.filterNotNull(),
                selectedDst.filterNotNull(),
            ) { src, dst -> src to dst }.distinctUntilChanged().combine(
                    srcAmountState.textAsFlow().filter { it.isNotEmpty() }) { address, amount ->
                    address to srcAmount
                }.combine(refreshQuoteState) { it, _ -> it }.debounce(450L)
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
                                ?: throw SwapException.SwapIsNotSupported("Swap is not supported for this pair")
                        this@SwapFormViewModel.provider = provider

                        val currency = appCurrencyRepository.currency.first()

                        val srcFiatValue = convertTokenValueToFiat(srcToken, tokenValue, currency)

                        val srcFiatValueText = srcFiatValue.let {
                            fiatValueToString(it)
                        }

                        val srcNativeToken = tokenRepository.getNativeToken(srcToken.chain.id)
                        val vultBPSDiscount = if (vaultId != null) {
                            getDiscountBpsUseCase.invoke(vaultId!!, provider)
                        } else {
                            0
                        }

                        when (provider) {
                            SwapProvider.MAYA, SwapProvider.THORCHAIN -> {
                                val srcUsdFiatValue = convertTokenValueToFiat(
                                    srcToken, tokenValue, AppCurrency.USD,
                                )

                                val isAffiliate =
                                    srcUsdFiatValue.value >= AFFILIATE_FEE_USD_THRESHOLD.toBigDecimal()

                                val (quote, recommendedMinAmountToken) = if (provider == SwapProvider.MAYA) {
                                    val mayaSwapQuote = swapQuoteRepository.getMayaSwapQuote(
                                        dstAddress = dst.address.address,
                                        srcToken = srcToken,
                                        dstToken = dstToken,
                                        tokenValue = tokenValue,
                                        isAffiliate = isAffiliate,
                                        bpsDiscount = vultBPSDiscount,
                                    )
                                    mayaSwapQuote as SwapQuote.MayaChain to mayaSwapQuote.recommendedMinTokenValue
                                } else {
                                    val referral = referralCode.value
                                        ?: vaultId?.takeIf { srcToken.chain.id == Chain.ThorChain.id }
                                            ?.let { referralRepository.getExternalReferralBy(it) }

                                    referral?.let { code -> referralCode.update { code } }

                                    val thorSwapQuote = swapQuoteRepository.getSwapQuote(
                                        dstAddress = dst.address.address,
                                        srcToken = srcToken,
                                        dstToken = dstToken,
                                        tokenValue = tokenValue,
                                        isAffiliate = isAffiliate,
                                        referralCode = referral.orEmpty(),
                                        bpsDiscount = vultBPSDiscount,
                                    )
                                    thorSwapQuote as SwapQuote.ThorChain to thorSwapQuote.recommendedMinTokenValue
                                }
                                this@SwapFormViewModel.quote = quote

                                val recommendedMinAmountTokenString =
                                    mapTokenValueToDecimalUiString(recommendedMinAmountToken)
                                amount.let {
                                    if (amount < recommendedMinAmountToken.decimal) {
                                        throw SwapException.SmallSwapAmount(
                                            recommendedMinAmountTokenString
                                        )
                                    }
                                }

                                val fiatFees =
                                    convertTokenValueToFiat(dstToken, quote.fees, currency)
                                swapFeeFiat.value = fiatFees

                                val estimatedDstTokenValue = mapTokenValueToDecimalUiString(
                                    quote.expectedDstValue
                                )

                                val estimatedDstFiatValue = convertTokenValueToFiat(
                                    dstToken, quote.expectedDstValue, currency
                                )

                                uiState.update {
                                    it.copy(
                                        provider = if (provider == SwapProvider.MAYA) R.string.swap_form_provider_mayachain.asUiText()
                                        else R.string.swap_form_provider_thorchain.asUiText(),
                                        srcFiatValue = srcFiatValueText,
                                        estimatedDstTokenValue = estimatedDstTokenValue,
                                        estimatedDstFiatValue = fiatValueToString(
                                            estimatedDstFiatValue
                                        ),
                                        fee = fiatValueToString(fiatFees),
                                        formError = null,
                                        isSwapDisabled = false,
                                        isLoading = false,
                                        expiredAt = this@SwapFormViewModel.quote?.expiredAt,
                                    )
                                }
                            }

                            SwapProvider.KYBER -> {
                                val srcUsdFiatValue = convertTokenValueToFiat(
                                    srcToken,
                                    tokenValue,
                                    AppCurrency.USD,
                                )
                                val isAffiliate =
                                    srcUsdFiatValue.value >= AFFILIATE_FEE_USD_THRESHOLD.toBigDecimal()
                                val quote = swapQuoteRepository.getKyberSwapQuote(
                                    srcToken = srcToken,
                                    dstToken = dstToken,
                                    tokenValue = tokenValue,
                                    isAffiliate = isAffiliate,
                                )
                                val expectedDstValue = TokenValue(
                                    value = quote.dstAmount.toBigInteger(),
                                    token = dstToken,
                                )
                                val tokenFees =
                                    TokenValue(value = quote.tx.gasPrice.toBigInteger() * (quote.tx.gas.takeIf { it != 0L }
                                        ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT).toBigInteger(),
                                        token = srcNativeToken)

                                this@SwapFormViewModel.quote = SwapQuote.OneInch(
                                    expectedDstValue = expectedDstValue,
                                    fees = tokenFees,
                                    data = quote,
                                    expiredAt = Clock.System.now() + expiredAfter,
                                    provider = provider.getSwapProviderId(),
                                )

                                val fiatFees =
                                    convertTokenValueToFiat(srcNativeToken, tokenFees, currency)
                                swapFeeFiat.value = fiatFees

                                val estimatedDstTokenValue =
                                    mapTokenValueToDecimalUiString(expectedDstValue)

                                val estimatedDstFiatValue = convertTokenValueToFiat(
                                    dstToken, expectedDstValue, currency
                                )

                                uiState.update {
                                    it.copy(
                                        provider = R.string.swap_for_provider_kyber.asUiText(),
                                        srcFiatValue = srcFiatValueText,
                                        estimatedDstTokenValue = estimatedDstTokenValue,
                                        estimatedDstFiatValue = fiatValueToString(
                                            estimatedDstFiatValue
                                        ),
                                        fee = fiatValueToString(fiatFees),
                                        formError = null,
                                        isSwapDisabled = false,
                                        isLoading = false,
                                        expiredAt = this@SwapFormViewModel.quote?.expiredAt,
                                    )
                                }
                            }

                            SwapProvider.ONEINCH -> {
                                val srcUsdFiatValue = convertTokenValueToFiat(
                                    srcToken, tokenValue, AppCurrency.USD,
                                )

                                val isAffiliate =
                                    srcUsdFiatValue.value >= AFFILIATE_FEE_USD_THRESHOLD.toBigDecimal()

                                val quote = swapQuoteRepository.getOneInchSwapQuote(
                                    srcToken = srcToken,
                                    dstToken = dstToken,
                                    tokenValue = tokenValue,
                                    isAffiliate = isAffiliate,
                                    bpsDiscount = vultBPSDiscount,
                                )

                                val expectedDstValue = TokenValue(
                                    value = quote.dstAmount.toBigInteger(),
                                    token = dstToken,
                                )

                                val tokenFees =
                                    TokenValue(value = quote.tx.gasPrice.toBigInteger() * (quote.tx.gas.takeIf { it != 0L }
                                        ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT).toBigInteger(),
                                        token = srcNativeToken)

                                this@SwapFormViewModel.quote = SwapQuote.OneInch(
                                    expectedDstValue = expectedDstValue,
                                    fees = tokenFees,
                                    data = quote,
                                    expiredAt = Clock.System.now() + expiredAfter,
                                    provider = provider.getSwapProviderId(),
                                )

                                val fiatFees =
                                    convertTokenValueToFiat(srcNativeToken, tokenFees, currency)
                                swapFeeFiat.value = fiatFees

                                val estimatedDstTokenValue =
                                    mapTokenValueToDecimalUiString(expectedDstValue)

                                val estimatedDstFiatValue = convertTokenValueToFiat(
                                    dstToken, expectedDstValue, currency
                                )

                                uiState.update {
                                    it.copy(
                                        provider = R.string.swap_for_provider_1inch.asUiText(),
                                        srcFiatValue = srcFiatValueText,
                                        estimatedDstTokenValue = estimatedDstTokenValue,
                                        estimatedDstFiatValue = fiatValueToString(
                                            estimatedDstFiatValue
                                        ),
                                        fee = fiatValueToString(fiatFees),
                                        formError = null,
                                        isSwapDisabled = false,
                                        isLoading = false,
                                        expiredAt = this@SwapFormViewModel.quote?.expiredAt,
                                    )
                                }
                            }

                            SwapProvider.LIFI, SwapProvider.JUPITER -> {
                                val quote =
                                    if (provider == SwapProvider.LIFI) swapQuoteRepository.getLiFiSwapQuote(
                                        srcAddress = src.address.address,
                                        dstAddress = dst.address.address,
                                        srcToken = srcToken,
                                        dstToken = dstToken,
                                        tokenValue = tokenValue,
                                    ) else swapQuoteRepository.getJupiterSwapQuote(
                                        srcAddress = src.address.address,
                                        srcToken = srcToken,
                                        dstToken = dstToken,
                                        tokenValue = tokenValue
                                    )

                                val expectedDstValue = TokenValue(
                                    value = quote.dstAmount.toBigInteger(),
                                    token = dstToken,
                                )

                                val (feeAmount, feeCoin) = try {
                                    if (quote.tx.swapFeeTokenContract.isNotEmpty()) {
                                        val tokenContract = quote.tx.swapFeeTokenContract
                                        val chainId = srcNativeToken.chain.id
                                        val amount = quote.tx.swapFee.toBigInteger()
                                        val coinAndFiatValue = searchToken(chainId, tokenContract)
                                            ?: error("Can't find token or price")
                                        val newNativeAmount =
                                            convertTokenToTokenUseCase.convertTokenToToken(amount, coinAndFiatValue, srcNativeToken)
                                        Pair(newNativeAmount, srcNativeToken)
                                    } else {
                                        Pair(quote.tx.swapFee.toBigInteger(), srcNativeToken)
                                    }
                                } catch (t: Throwable) {
                                    Timber.e(t)
                                    Pair(BigInteger.ZERO, srcNativeToken)
                                }

                                val updatedTx = quote.tx.copy(swapFee = feeAmount.toString())

                                val tokenFees = TokenValue(
                                    value = feeAmount, token = feeCoin
                                )

                                this@SwapFormViewModel.quote = SwapQuote.OneInch(
                                    expectedDstValue = expectedDstValue,
                                    fees = tokenFees,
                                    data = quote.copy(tx = updatedTx),
                                    expiredAt = Clock.System.now() + expiredAfter,
                                    provider = provider.getSwapProviderId(),
                                )

                                val fiatFees = convertTokenValueToFiat(feeCoin, tokenFees, currency)
                                swapFeeFiat.value = fiatFees
                                val estimatedDstTokenValue =
                                    mapTokenValueToDecimalUiString(expectedDstValue)

                                val estimatedDstFiatValue = convertTokenValueToFiat(
                                    dstToken, expectedDstValue, currency
                                )

                                uiState.update {
                                    it.copy(
                                        provider = if (provider == SwapProvider.LIFI) {
                                            R.string.swap_for_provider_li_fi.asUiText()
                                        } else {
                                            R.string.swap_for_provider_jupiter.asUiText()
                                        },
                                        srcFiatValue = srcFiatValueText,
                                        estimatedDstTokenValue = estimatedDstTokenValue,
                                        estimatedDstFiatValue = fiatValueToString(
                                            estimatedDstFiatValue
                                        ),
                                        fee = fiatValueToString(fiatFees),
                                        formError = null,
                                        isSwapDisabled = false,
                                        isLoading = false,
                                        expiredAt = this@SwapFormViewModel.quote?.expiredAt,
                                    )
                                }
                            }
                        }
                    } catch (e: SwapException) {
                        this@SwapFormViewModel.quote = null
                        val formError = when (e) {
                            is SwapException.SwapIsNotSupported -> UiText.StringResource(R.string.swap_route_not_available)

                            is SwapException.AmountCannotBeZero -> UiText.StringResource(R.string.swap_form_invalid_amount)

                            is SwapException.SameAssets -> UiText.StringResource(R.string.swap_screen_same_asset_error_message)

                            is SwapException.UnkownSwapError -> UiText.DynamicString(
                                e.message ?: "Unknown error"
                            )

                            is SwapException.InsufficentSwapAmount -> UiText.StringResource(R.string.swap_error_amount_too_low)

                            is SwapException.SwapRouteNotAvailable -> UiText.StringResource(R.string.swap_route_not_available)

                            is SwapException.TimeOut -> UiText.StringResource(R.string.swap_error_time_out)

                            is SwapException.NetworkConnection -> UiText.StringResource(R.string.network_connection_lost)

                            is SwapException.SmallSwapAmount -> {
                                e.message?.let {
                                    UiText.FormattedText(
                                        R.string.swap_form_minimum_amount,
                                        listOf(it, uiState.value.selectedSrcToken?.title ?: "")
                                    )
                                } ?: run {
                                    UiText.StringResource(R.string.swap_error_amount_too_low)
                                }
                            }

                            is SwapException.InsufficientFunds -> UiText.StringResource(R.string.swap_error_small_insufficient_funds)
                        }
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
                        Timber.e("swapError $e")
                    } catch (e: Exception) {
                        this@SwapFormViewModel.quote = null
                        // TODO handle error
                        isLoading = false
                        Timber.e(e)
                    }

                    this@SwapFormViewModel.quote?.expiredAt?.let {
                        launchRefreshQuoteTimer(it)
                    }
                }
        }
    }

    private fun launchRefreshQuoteTimer(expiredAt: Instant) {
        refreshQuoteJob?.cancel()
        refreshQuoteJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                delay(expiredAt - Clock.System.now())
                refreshQuoteState.value++
            }
        }
    }

    private fun validateSrcAmount(srcAmount: String): UiText? {
        if (srcAmount.isEmpty() || srcAmount.length > TextFieldUtils.AMOUNT_MAX_LENGTH) {
            return UiText.StringResource(R.string.swap_form_invalid_amount)
        }
        val srcAmountAmountBigDecimal = srcAmount.toBigDecimalOrNull()
        if (srcAmountAmountBigDecimal == null || srcAmountAmountBigDecimal <= BigDecimal.ZERO) {
            return UiText.StringResource(R.string.swap_error_no_amount)
        }
        return null
    }

    fun hideError() {
        uiState.update {
            it.copy(error = null)
        }
    }

    private fun showError(error: UiText) {
        uiState.update {
            it.copy(error = error)
        }
    }

    private fun getGasLimit(
        token: Coin
    ): BigInteger? {
        val isEVMSwap = token.isNativeToken && token.chain in listOf(Chain.Ethereum, Chain.Arbitrum)
        return if (isEVMSwap) BigInteger.valueOf(
            if (token.chain == Chain.Ethereum) ETH_GAS_LIMIT else ARB_GAS_LIMIT
        ) else null
    }

    companion object {
        const val AFFILIATE_FEE_USD_THRESHOLD = 100
        const val ETH_GAS_LIMIT: Long = 40_000
        const val ARB_GAS_LIMIT: Long = 400_000

        private const val ARG_SELECTED_SRC_TOKEN_ID = "ARG_SELECTED_SRC_TOKEN_ID"
        private const val ARG_SELECTED_DST_TOKEN_ID = "ARG_SELECTED_DST_TOKEN_ID"

    }

}


internal fun MutableStateFlow<SendSrc?>.updateSrc(
    selectedTokenId: String?,
    addresses: List<Address>,
    chain: Chain?,
) {
    val selectedSrcValue = value
    value = if (addresses.isEmpty()) {
        null
    } else {
        if (selectedSrcValue == null) {
            addresses.firstSendSrc(selectedTokenId, chain)
        } else {
            addresses.findCurrentSrc(selectedTokenId, selectedSrcValue)
        }
    }
}

internal fun List<Address>.firstSendSrc(
    selectedTokenId: String?,
    filterByChain: Chain?,
): SendSrc {
    val address = when {
        selectedTokenId != null -> first { it -> it.accounts.any { it.token.id == selectedTokenId } }
        filterByChain != null -> first { it.chain == filterByChain }
        else -> first()
    }

    val account = when {
        selectedTokenId != null -> address.accounts.first { it.token.id == selectedTokenId }
        filterByChain != null -> address.accounts.first { it.token.isNativeToken }
        else -> address.accounts.first()
    }

    return SendSrc(address, account)
}

internal fun List<Address>.findCurrentSrc(
    selectedTokenId: String?,
    currentSrc: SendSrc,
): SendSrc {
    if (selectedTokenId == null) {
        val selectedAddress = currentSrc.address
        val selectedAccount = currentSrc.account
        val address = first {
            it.chain == selectedAddress.chain && it.address == selectedAddress.address
        }
        return SendSrc(
            address,
            address.accounts.first {
                it.token.ticker == selectedAccount.token.ticker
            },
        )
    } else {
        return firstSendSrc(selectedTokenId, null)
    }
}