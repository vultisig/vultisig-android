package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.textAsFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.chains.EvmHelper
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.common.asUiText
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.AppCurrency
import com.vultisig.wallet.data.models.OneInchSwapPayloadJson
import com.vultisig.wallet.data.models.SwapPayload
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.TokenValue
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
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.IsSwapSupported
import com.vultisig.wallet.models.THORChainSwapPayload
import com.vultisig.wallet.presenter.common.TextFieldUtils
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.DurationToUiStringMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.mappers.ZeroValueCurrencyToStringMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val gas: String = "",
    val fee: String = "",
    val error: UiText? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@HiltViewModel
internal class SwapFormViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val sendNavigator: Navigator<SendDst>,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
    private val mapTokenValueToString: TokenValueToStringWithUnitMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToString: FiatValueToStringMapper,
    private val zeroValueCurrencyToString: ZeroValueCurrencyToStringMapper,
    private val mapDurationToUiString: DurationToUiStringMapper,

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
) : ViewModel() {

    val uiState = MutableStateFlow(SwapFormUiModel())

    val srcAmountState = TextFieldState()

    private var vaultId: String? = null
    private var chainId: String? = null

    private var quote: SwapQuote? = null

    private val srcAmount: BigDecimal?
        get() = srcAmountState.text.toString().toBigDecimalOrNull()

    private val selectedSrc = MutableStateFlow<SendSrc?>(null)
    private val selectedDst = MutableStateFlow<SendSrc?>(null)
    private val selectedSrcId = MutableStateFlow<String?>(null)
    private val selectedDstId = MutableStateFlow<String?>(null)

    private val gasFee = MutableStateFlow<TokenValue?>(null)

    init {
        collectSelectedAccounts()

        calculateGas()
        calculateFees()
    }

    fun swap() {
        try {
            // TODO verify swap info
            val vaultId = vaultId ?: return
            val selectedSrc = selectedSrc.value ?: return
            val selectedDst = selectedDst.value ?: return

            val gasFee = gasFee.value ?: return

            val srcToken = selectedSrc.account.token
            val dstToken = selectedDst.account.token

            if (srcToken == dstToken) {
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.swap_screen_same_asset_error_message)
                )
            }

            val srcAddress = selectedSrc.address.address

            val srcAmountInt = srcAmount
                ?.movePointRight(selectedSrc.account.token.decimal)
                ?.toBigInteger()

            val selectedSrcBalance = selectedSrc.account.tokenValue?.value ?: return

            val srcTokenValue = srcAmountInt
                ?.let { convertTokenAndValueToTokenValue(srcToken, it) }
                ?: return


            if (srcToken.isNativeToken) {
                if (srcAmountInt + gasFee.value > selectedSrcBalance) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_insufficient_balance)
                    )
                }
            } else {
                val nativeTokenAccount = selectedSrc.address.accounts.find { it.token.isNativeToken }
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


            val quote = quote ?: return

            viewModelScope.launch {
                val dstTokenValue = quote.expectedDstValue

                val specificAndUtxo = blockChainSpecificRepository.getSpecific(
                    srcToken.chain,
                    srcAddress,
                    srcToken,
                    gasFee,
                    isSwap = true,
                )

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

                        SwapTransaction(
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
                                    steamingInterval = "1",
                                    streamingQuantity = "0",
                                    expirationTime = (System.currentTimeMillis().milliseconds + 15.minutes)
                                        .inWholeSeconds.toULong(),
                                    isAffiliate = isAffiliate,
                                )
                            )
                        )
                    }

                    is SwapQuote.MayaChain -> {
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

                        SwapTransaction(
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
                                    steamingInterval = "1",
                                    streamingQuantity = "0",
                                    expirationTime = (System.currentTimeMillis().milliseconds + 15.minutes)
                                        .inWholeSeconds.toULong(),
                                    isAffiliate = isAffiliate,
                                )
                            )
                        )
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

                        SwapTransaction(
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
                    )
                )
            }
        } catch (e: InvalidTransactionDataException) {
            showError(e.text)
            return
        }
    }

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
        if (targetArg == Destination.Swap.ARG_SELECTED_SRC_TOKEN_ID) {
            val src = requestResultRepository
                .request<Coin>(Destination.Swap.ARG_SELECTED_SRC_TOKEN_ID)
            selectedSrcId.update { src.id }
        } else {
            val dst = requestResultRepository
                .request<Coin>(Destination.Swap.ARG_SELECTED_DST_TOKEN_ID)
            selectedDstId.update { dst.id }
        }
    }

    fun flipSelectedTokens() {
        val buffer = selectedSrc.value
        selectedSrc.value = selectedDst.value
        selectedDst.value = buffer
    }

    fun loadData(
        vaultId: String,
        chainId: String?,
    ) {
        this.vaultId = vaultId
        this.chainId = chainId

        viewModelScope.launch {
            loadTokens(
                selectedSrcId.value,
                selectedDstId.value,
                vaultId,
                chainId,
            )
        }
    }

    fun validateAmount() {
        val errorMessage = validateSrcAmount(srcAmountState.text.toString())
        uiState.update { it.copy(error = errorMessage) }
    }

    private fun loadTokens(
        selectedSrcTokenId: String? = null,
        selectedDstTokenId: String? = null,
        vaultId: String,
        chainId: String?,
    ) {
        val chain = chainId?.let(Chain::fromRaw)

        viewModelScope.launch {
            accountsRepository.loadAddresses(vaultId)
                .map { addresses ->
                    addresses.filter { it.chain.IsSwapSupported }
                }
                .catch {
                    // TODO handle error
                    Timber.e(it)
                }.collect { addresses ->
                    try {
                        selectedSrc.updateSrc(selectedSrcTokenId, addresses, chain)
                        selectedDst.updateSrc(selectedDstTokenId, addresses, chain)
                    } catch (ex: Exception) {
                        Timber.e(ex)
                    }
                }
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
                selectedSrcId.update { src?.account?.token?.id }
                selectedDstId.update { dst?.account?.token?.id }
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
                .map { it?.address }
                .filterNotNull()
                .map {
                    gasFeeRepository.getGasFee(it.chain, it.address)
                }
                .catch {
                    // TODO handle error when querying gas fee
                    Timber.e(it)
                }
                .collect { gasFee ->
                    this@SwapFormViewModel.gasFee.value = gasFee

                    uiState.update {
                        it.copy(gas = mapTokenValueToString(gasFee))
                    }
                }
        }
    }

    private fun calculateFees() {
        viewModelScope.launch {
            combine(
                selectedSrc.filterNotNull(),
                selectedDst.filterNotNull(),
            ) { src, dst -> src to dst }
                .distinctUntilChanged()
                .combine(srcAmountState.textAsFlow()) { addrs, amount ->
                    addrs to srcAmount
                }
                .collect { (addrs, amount) ->
                    val (src, dst) = addrs

                    val srcToken = src.account.token
                    val dstToken = dst.account.token

                    val srcTokenValue = amount
                        ?.movePointRight(src.account.token.decimal)
                        ?.toBigInteger()

                    try {
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

                        val provider = swapQuoteRepository.resolveProvider(srcToken, dstToken)

                        when (provider) {
                            SwapProvider.MAYA, SwapProvider.THORCHAIN -> {
                                val quote = if (provider == SwapProvider.MAYA) {
                                    swapQuoteRepository.getMayaSwapQuote(
                                        dstAddress = dst.address.address,
                                        srcToken = srcToken,
                                        dstToken = dstToken,
                                        tokenValue = tokenValue,
                                    )
                                } else {
                                    swapQuoteRepository.getSwapQuote(
                                        dstAddress = dst.address.address,
                                        srcToken = srcToken,
                                        dstToken = dstToken,
                                        tokenValue = tokenValue,
                                    )
                                }
                                this@SwapFormViewModel.quote = quote

                                val fiatFees =
                                    convertTokenValueToFiat(dstToken, quote.fees, currency)

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
                                    )
                                }
                            }

                            else -> {
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
                                            EvmHelper.DefaultEthSwapGasUnit.toBigInteger(),
                                    token = srcNativeToken
                                )

                                this@SwapFormViewModel.quote = SwapQuote.OneInch(
                                    expectedDstValue = expectedDstValue,
                                    fees = tokenFees,
                                    data = quote
                                )

                                val fiatFees =
                                    convertTokenValueToFiat(srcNativeToken, tokenFees, currency)

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
                                    )
                                }
                            }
                        }
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

    companion object {

        private const val AFFILIATE_FEE_USD_THRESHOLD = 100

    }

}


internal fun MutableStateFlow<SendSrc?>.updateSrc(
    selectedTokenId: String?,
    addresses: List<Address>,
    chain: Chain?,
    forceChainChange: Boolean = false,
) {
    val selectedSrcValue = value
    value = if (selectedSrcValue == null || forceChainChange) {
        addresses.firstSendSrc(selectedTokenId, chain)
    } else {
        addresses.findCurrentSrc(selectedTokenId, selectedSrcValue)
    }
}

internal fun List<Address>.firstSendSrc(
    selectedTokenId: String?,
    filterByChain: Chain?,
): SendSrc {
    val address = when {
        selectedTokenId != null -> first { it.accounts.any { it.token.id == selectedTokenId } }
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