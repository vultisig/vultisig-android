package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.IsSwapSupported
import com.vultisig.wallet.data.models.OneInchSwapPayloadJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapTransaction.RegularSwapTransaction
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.mappers.ZeroValueCurrencyToStringMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal data class SwapFormUiModel(
    val selectedSrcToken: TokenBalanceUiModel? = null,
    val selectedDstToken: TokenBalanceUiModel? = null,
    val srcFiatValue: String = "0",
    val estimatedDstTokenValue: String = "0",
    val estimatedDstFiatValue: String = "0",
    val provider: UiText = UiText.Empty,
    val minimumAmount: String = BigInteger.ZERO.toString(),
    val gas: String = "",
    val fiatGas: String = "",
    val totalFee: String = "0",
    val fee: String = "",
    val error: UiText? = null,
    val formError: UiText? = null,
    val isSwapDisabled: Boolean = false,
    val isLoading: Boolean = false,
)

@HiltViewModel
internal class SwapFormViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val sendNavigator: Navigator<SendDst>,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToString: FiatValueToStringMapper,
    private val zeroValueCurrencyToString: ZeroValueCurrencyToStringMapper,
    private val convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase,

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
) : ViewModel() {

    val uiState = MutableStateFlow(SwapFormUiModel())

    val srcAmountState = TextFieldState()

    private var vaultId: String? = null
    private var chain: Chain? = null

    private var quote: SwapQuote? = null

    private val srcAmount: BigDecimal?
        get() = srcAmountState.text.toString().toBigDecimalOrNull()

    private val selectedSrc = MutableStateFlow<SendSrc?>(null)
    private val selectedDst = MutableStateFlow<SendSrc?>(null)
    private val selectedSrcId = MutableStateFlow<String?>(null)
    private val selectedDstId = MutableStateFlow<String?>(null)

    private val gasFee = MutableStateFlow<TokenValue?>(null)
    private val swapFeeFiat = MutableStateFlow<FiatValue?>(null)
    private val gasFeeFiat = MutableStateFlow<FiatValue?>(null)

    private val addresses = MutableStateFlow<List<Address>>(emptyList())

    private var selectTokensJob: Job? = null

    init {
        collectSelectedAccounts()
        collectSelectedTokens()

        calculateGas()
        calculateFees()
        collectTotalFee()
    }

    fun swap() {
        try {
            // TODO verify swap info
            val vaultId = vaultId ?: return
            val selectedSrc = selectedSrc.value ?: return
            val selectedDst = selectedDst.value ?: return

            val gasFee = gasFee.value ?: return
            val gasFeeFiatValue = gasFeeFiat.value ?: return

            val srcToken = selectedSrc.account.token
            val selectedSrcBalance = selectedSrc.account.tokenValue?.value ?: return

            val srcAmountInt = srcAmount
                ?.movePointRight(selectedSrc.account.token.decimal)
                ?.toBigInteger()

            if (srcAmountInt == BigInteger.ZERO) return

            val srcTokenValue = srcAmountInt
                ?.let { convertTokenAndValueToTokenValue(srcToken, it) }
                ?: return

            val quote = quote ?: return

            showLoading()

            val dstToken = selectedDst.account.token

            if (srcToken == dstToken) {
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.swap_screen_same_asset_error_message)
                )
            }

            val srcAddress = selectedSrc.address.address

            if (srcToken.isNativeToken) {
                if (srcAmountInt + gasFee.value > selectedSrcBalance) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_insufficient_balance)
                    )
                }
            } else {
                val nativeTokenAccount =
                    selectedSrc.address.accounts.find { it.token.isNativeToken }
                val nativeTokenValue = nativeTokenAccount?.tokenValue?.value
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_token)
                    )

                if (selectedSrcBalance < srcAmountInt
                    || nativeTokenValue < gasFee.value
                ) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_insufficient_balance)
                    )
                }
            }

            viewModelScope.launch {
                val dstTokenValue = quote.expectedDstValue

                val specificAndUtxo = getSpecificAndUtxo(srcToken, srcAddress, gasFee)

                val transaction = when (quote) {
                    is SwapQuote.ThorChain -> {
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

                        val isAffiliate = srcFiatValue.value >=
                                AFFILIATE_FEE_USD_THRESHOLD.toBigDecimal()

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
                            isApprovalRequired = isApprovalRequired,
                            memo = null,
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
                                    expirationTime = (System.currentTimeMillis().milliseconds + 15.minutes)
                                        .inWholeSeconds.toULong(),
                                    isAffiliate = isAffiliate,
                                )
                            )
                        )
                    }

                    is SwapQuote.MayaChain -> {
                        val address = quote.data.inboundAddress
                        val dstAddress = address ?: srcAddress

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

                        val isAffiliate = srcFiatValue.value >=
                                AFFILIATE_FEE_USD_THRESHOLD.toBigDecimal()

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
                                    expirationTime = (System.currentTimeMillis().milliseconds + 15.minutes)
                                        .inWholeSeconds.toULong(),
                                    isAffiliate = isAffiliate,
                                )
                            )
                        )

                        regularSwapTransaction
                    }

                    is SwapQuote.OneInch -> {
                        val dstAddress = quote.data.tx.to

                        val allowance = allowanceRepository.getAllowance(
                            chain = srcToken.chain,
                            contractAddress = srcToken.contractAddress,
                            srcAddress = srcAddress,
                            dstAddress = dstAddress,
                        )
                        val isApprovalRequired =
                            allowance != null && allowance < srcTokenValue.value

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
                            memo = null,
                            isApprovalRequired = isApprovalRequired,
                            gasFeeFiatValue = gasFeeFiatValue,
                            payload = SwapPayload.OneInch(
                                OneInchSwapPayloadJson(
                                    fromCoin = srcToken,
                                    toCoin = dstToken,
                                    fromAmount = srcTokenValue.value,
                                    toAmountDecimal = dstTokenValue.decimal,
                                    quote = quote.data,
                                )
                            )
                        )
                    }
                }

                swapTransactionRepository.addTransaction(transaction)

                sendNavigator.navigate(
                    SendDst.VerifyTransaction(
                        transactionId = transaction.id,
                        vaultId = vaultId,
                    )
                )
            }
        } catch (e: InvalidTransactionDataException) {
            showError(e.text)
            return
        } finally {
            hideLoading()
        }
    }

    private fun showLoading() {
        uiState.update {
            it.copy(
                isLoading = true
            )
        }
    }
    private fun hideLoading() {
        uiState.update {
            it.copy(
                isLoading = false
            )
        }
    }

    private suspend fun getSpecificAndUtxo(
        srcToken: Coin,
        srcAddress: String,
        gasFee: TokenValue,
    ) = blockChainSpecificRepository.getSpecific(
        srcToken.chain,
        srcAddress,
        srcToken,
        gasFee,
        isSwap = true,
        isMaxAmountEnabled = false,
        isDeposit = srcToken.chain == Chain.MayaChain,
        gasLimit = getGasLimit(srcToken),
    )


    fun selectSrcToken() {
        navigateToSelectToken(Destination.Swap.ARG_SELECTED_SRC_TOKEN_ID)
    }

    fun selectDstToken() {
        navigateToSelectToken(Destination.Swap.ARG_SELECTED_DST_TOKEN_ID)
    }

    private fun navigateToSelectToken(
        targetArg: String,
    ) {
        viewModelScope.launch {
            navigator.navigate(
                Destination.SelectToken(
                    vaultId = vaultId ?: return@launch,
                    targetArg = targetArg,
                    swapSelect = true,
                )
            )
            checkTokenSelectionResponse(targetArg)
        }
    }

    private suspend fun checkTokenSelectionResponse(targetArg: String) {
        val result = requestResultRepository.request<Coin>(targetArg).id
        if (targetArg == Destination.Swap.ARG_SELECTED_SRC_TOKEN_ID) {
            selectedSrcId.value = result
        } else {
            selectedDstId.value = result
        }
    }

    fun flipSelectedTokens() {
        viewModelScope.launch {
            val buffer = selectedSrc.value
            selectedSrc.value = selectedDst.value
            selectedDst.value = buffer
        }

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
            accountsRepository.loadAddresses(vaultId)
                .map { addresses ->
                    addresses.filter { it.chain.IsSwapSupported }
                }
                .catch {
                    // TODO handle error
                    Timber.e(it)
                }.collect(addresses)
        }
    }

    private fun collectSelectedTokens() {
        selectTokensJob?.cancel()
        selectTokensJob = viewModelScope.launch {
            combine(
                addresses,
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
                val srcUiModel = src?.let(accountToTokenBalanceUiModelMapper::map)
                val dstUiModel = dst?.let(accountToTokenBalanceUiModelMapper::map)

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
            selectedSrc
                .filterNotNull()
                .map {
                    it to gasFeeRepository.getGasFee(it.address.chain, it.address.address)
                }
                .catch {
                    // TODO handle error when querying gas fee
                    Timber.e(it)
                }
                .collect { (selectedSrc, gasFee) ->
                    this@SwapFormViewModel.gasFee.value = gasFee
                    val selectedAccount = selectedSrc.account
                    val chain = selectedAccount.token.chain
                    val selectedToken = selectedAccount.token
                    val srcAddress = selectedAccount.token.address
                    val spec = getSpecificAndUtxo(selectedToken, srcAddress, gasFee)

                    val estimatedFee = gasFeeToEstimatedFee(
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

                    gasFeeFiat.value = estimatedFee.fiatValue

                    uiState.update {
                        it.copy(
                            gas = estimatedFee.formattedTokenValue,
                            fiatGas = estimatedFee.formattedFiatValue,
                        )
                    }
                }
        }
    }

    private fun collectTotalFee() {
        gasFeeFiat.filterNotNull().combine(swapFeeFiat.filterNotNull()) { a, b ->
            a + b
        }.onEach { totalFee ->
            uiState.update {
                it.copy(totalFee = fiatValueToString.map(totalFee))
            }
        }.launchIn(viewModelScope)
    }

    private fun calculateFees() {
        viewModelScope.launch {
            combine(
                selectedSrc.filterNotNull(),
                selectedDst.filterNotNull(),
            ) { src, dst -> src to dst }
                .distinctUntilChanged()
                .combine(srcAmountState.textAsFlow()) { address, amount ->
                    address to srcAmount
                }
                .collect { (address, amount) ->
                    val (src, dst) = address

                    val srcToken = src.account.token
                    val dstToken = dst.account.token

                    val srcTokenValue = amount
                        ?.movePointRight(src.account.token.decimal)
                        ?.toBigInteger()

                    try {
                        if (srcTokenValue == null || srcTokenValue <= BigInteger.ZERO) {
                            throw SwapException.AmountCannotBeZero("Amount must be positive")
                        }
                        if (srcToken == dstToken) {
                            throw SwapException.SameAssets("Can't swap same assets ${srcToken.id})")
                        }

                        val provider = swapQuoteRepository.resolveProvider(srcToken, dstToken)
                            ?: throw SwapException.SwapIsNotSupported("Swap is not supported for this pair")

                        val hasUserSetTokenValue = srcTokenValue != null

                        val tokenValue = srcTokenValue?.let {
                            convertTokenAndValueToTokenValue(srcToken, srcTokenValue)
                        } ?: TokenValue(
                            1.toBigInteger()
                                .multiply(BigInteger.TEN.pow(srcToken.decimal)),
                            srcToken.ticker,
                            srcToken.decimal
                        )

                        val currency = appCurrencyRepository.currency.first()

                        val srcFiatValue = if (hasUserSetTokenValue) {
                            convertTokenValueToFiat(srcToken, tokenValue, currency)
                        } else null

                        val srcFiatValueText = srcFiatValue?.let {
                            fiatValueToString.map(it)
                        } ?: zeroValueCurrencyToString.map(currency)

                        val srcNativeToken = tokenRepository.getNativeToken(srcToken.chain.id)

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
                                    )
                                    mayaSwapQuote as SwapQuote.MayaChain to mayaSwapQuote.recommendedMinTokenValue
                                } else {
                                    val thorSwapQuote = swapQuoteRepository.getSwapQuote(
                                        dstAddress = dst.address.address,
                                        srcToken = srcToken,
                                        dstToken = dstToken,
                                        tokenValue = tokenValue,
                                        isAffiliate = isAffiliate,
                                    )
                                    thorSwapQuote as SwapQuote.ThorChain to thorSwapQuote.recommendedMinTokenValue
                                }
                                this@SwapFormViewModel.quote = quote

                                val recommendedMinAmountTokenString =
                                    mapTokenValueToDecimalUiString(recommendedMinAmountToken)
                                amount?.let {
                                    uiState.update {
                                        if (amount < recommendedMinAmountTokenString.toBigDecimal()) {
                                            it.copy(
                                                minimumAmount = recommendedMinAmountTokenString,
                                                isSwapDisabled = true
                                            )
                                        } else {
                                            it.copy(
                                                minimumAmount = BigInteger.ZERO.toString(),
                                                isSwapDisabled = false
                                            )
                                        }
                                    }
                                }

                                val fiatFees =
                                    convertTokenValueToFiat(dstToken, quote.fees, currency)
                                swapFeeFiat.value = fiatFees

                                val estimatedDstTokenValue = if (hasUserSetTokenValue) {
                                    mapTokenValueToDecimalUiString(
                                        quote.expectedDstValue
                                    )
                                } else ""

                                val estimatedDstFiatValue = convertTokenValueToFiat(
                                    dstToken,
                                    quote.expectedDstValue,
                                    currency
                                )

                                uiState.update {
                                    it.copy(
                                        provider = if (provider == SwapProvider.MAYA)
                                            R.string.swap_form_provider_mayachain.asUiText()
                                        else
                                            R.string.swap_form_provider_thorchain.asUiText(),
                                        srcFiatValue = srcFiatValueText,
                                        estimatedDstTokenValue = estimatedDstTokenValue,
                                        estimatedDstFiatValue = fiatValueToString.map(
                                            estimatedDstFiatValue
                                        ),
                                        fee = fiatValueToString.map(fiatFees),
                                        formError = null,
                                        isSwapDisabled = false,
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
                                )

                                val expectedDstValue = TokenValue(
                                    value = quote.dstAmount.toBigInteger(),
                                    token = dstToken,
                                )

                                val tokenFees = TokenValue(
                                    value = quote.tx.gasPrice.toBigInteger() *
                                            (quote.tx.gas.takeIf { it != 0L }
                                                ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT).toBigInteger(),
                                    token = srcNativeToken
                                )

                                this@SwapFormViewModel.quote = SwapQuote.OneInch(
                                    expectedDstValue = expectedDstValue,
                                    fees = tokenFees,
                                    data = quote
                                )

                                val fiatFees =
                                    convertTokenValueToFiat(srcNativeToken, tokenFees, currency)
                                swapFeeFiat.value = fiatFees

                                val estimatedDstTokenValue = if (hasUserSetTokenValue) {
                                    mapTokenValueToDecimalUiString(expectedDstValue)
                                } else ""

                                val estimatedDstFiatValue = convertTokenValueToFiat(
                                    dstToken,
                                    expectedDstValue, currency
                                )

                                uiState.update {
                                    it.copy(
                                        provider = R.string.swap_for_provider_1inch.asUiText(),
                                        srcFiatValue = srcFiatValueText,
                                        estimatedDstTokenValue = estimatedDstTokenValue,
                                        estimatedDstFiatValue = fiatValueToString.map(
                                            estimatedDstFiatValue
                                        ),
                                        fee = fiatValueToString.map(fiatFees),
                                        formError = null,
                                        isSwapDisabled = false
                                    )
                                }
                            }

                            SwapProvider.LIFI -> {
                                val quote = swapQuoteRepository.getLiFiSwapQuote(
                                    srcAddress = src.address.address,
                                    dstAddress = dst.address.address,
                                    srcToken = srcToken,
                                    dstToken = dstToken,
                                    tokenValue = tokenValue,
                                )

                                val expectedDstValue = TokenValue(
                                    value = quote.dstAmount.toBigInteger(),
                                    token = dstToken,
                                )

                                val tokenFees = TokenValue(
                                    value = quote.tx.gasPrice.toBigInteger()
                                            * (quote.tx.gas.takeIf { it != 0L }
                                        ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT).toBigInteger(),
                                    token = srcNativeToken
                                )

                                this@SwapFormViewModel.quote = SwapQuote.OneInch(
                                    expectedDstValue = expectedDstValue,
                                    fees = tokenFees,
                                    data = quote
                                )

                                val fiatFees =
                                    convertTokenValueToFiat(srcNativeToken, tokenFees, currency)
                                swapFeeFiat.value = fiatFees
                                val estimatedDstTokenValue = if (hasUserSetTokenValue) {
                                    mapTokenValueToDecimalUiString(expectedDstValue)
                                } else ""

                                val estimatedDstFiatValue = convertTokenValueToFiat(
                                    dstToken,
                                    expectedDstValue, currency
                                )

                                uiState.update {
                                    it.copy(
                                        provider = R.string.swap_for_provider_li_fi.asUiText(),
                                        srcFiatValue = srcFiatValueText,
                                        estimatedDstTokenValue = estimatedDstTokenValue,
                                        estimatedDstFiatValue = fiatValueToString.map(
                                            estimatedDstFiatValue
                                        ),
                                        fee = fiatValueToString.map(fiatFees),
                                        formError = null,
                                        isSwapDisabled = false
                                    )
                                }
                            }
                        }
                    } catch (e: SwapException) {
                        val formError = when (e) {
                            is SwapException.SwapIsNotSupported ->
                                UiText.StringResource(R.string.swap_route_not_available)

                            is SwapException.AmountCannotBeZero ->
                                UiText.StringResource(R.string.swap_form_invalid_amount)

                            is SwapException.SameAssets ->
                                UiText.StringResource(R.string.swap_screen_same_asset_error_message)

                            is SwapException.UnkownSwapError ->
                                UiText.DynamicString(e.message ?: "Unknown error")

                            is SwapException.InsufficentSwapAmount ->
                                UiText.StringResource(R.string.swap_error_amount_too_low)

                        }
                        uiState.update {
                            it.copy(
                                provider = UiText.Empty,
                                srcFiatValue = "0",
                                estimatedDstTokenValue = "0",
                                estimatedDstFiatValue = "0",
                                fee = "0",
                                isSwapDisabled = true,
                                formError = formError
                            )
                        }
                        Timber.e("swapError $e")
                    } catch (e: Exception) {
                        // TODO handle error
                        Timber.e(e)
                    }
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
        val isEVMSwap =
            token.isNativeToken &&
                    token.chain in listOf(Chain.Ethereum, Chain.Arbitrum)
        return if (isEVMSwap)
            BigInteger.valueOf(
                if (token.chain == Chain.Ethereum)
                    ETH_GAS_LIMIT else ARB_GAS_LIMIT
            ) else null
    }

    companion object {
        const val AFFILIATE_FEE_USD_THRESHOLD = 100
        const val ETH_GAS_LIMIT: Long = 40_000
        const val ARB_GAS_LIMIT: Long = 400_000
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
            it.chain == selectedAddress.chain &&
                    it.address == selectedAddress.address
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