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
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.IsSwapSupported
import com.vultisig.wallet.models.THORChainSwapPayload
import com.vultisig.wallet.presenter.common.TextFieldUtils
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.DurationToUiStringMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
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
    val availableTokens: List<TokenBalanceUiModel> = emptyList(),
    val srcFiatValue: String = "0$",
    val estimatedDstTokenValue: String = "0",
    val estimatedDstFiatValue: String = "0$",
    val provider: UiText = UiText.Empty,
    val gas: String = "",
    val fee: String = "",
    val estimatedTime: UiText = UiText.DynamicString(""),
    val amountError: UiText? = null
)

@OptIn(ExperimentalFoundationApi::class)
@HiltViewModel
internal class SwapFormViewModel @Inject constructor(
    private val sendNavigator: Navigator<SendDst>,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
    private val mapTokenValueToString: TokenValueToStringWithUnitMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToString: FiatValueToStringMapper,
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
) : ViewModel() {

    val uiState = MutableStateFlow(SwapFormUiModel())

    val srcAmountState = TextFieldState()

    private var vaultId: String? = null

    private var quote: SwapQuote? = null

    private val srcAmount: BigDecimal?
        get() = srcAmountState.text.toString().toBigDecimalOrNull()

    private val selectedSrc = MutableStateFlow<SendSrc?>(null)
    private val selectedDst = MutableStateFlow<SendSrc?>(null)

    private val gasFee = MutableStateFlow<TokenValue?>(null)

    init {
        collectSelectedAccounts()

        calculateGas()
        calculateFees()
    }

    fun swap() {
        // TODO verify swap info
        val vaultId = vaultId ?: return
        val selectedSrc = selectedSrc.value ?: return
        val selectedDst = selectedDst.value ?: return

        val gasFee = gasFee.value ?: return

        val srcToken = selectedSrc.account.token
        val dstToken = selectedDst.account.token

        val srcAddress = selectedSrc.address.address

        val srcTokenValue = srcAmount
            ?.movePointRight(selectedSrc.account.token.decimal)
            ?.toBigInteger()
            ?.let { convertTokenAndValueToTokenValue(srcToken, it) }
            ?: return

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
                    val dstAddress = quote.data.router ?: quote.data.inboundAddress ?: srcAddress
                    val allowance = allowanceRepository.getAllowance(
                        chain = srcToken.chain,
                        contractAddress = srcToken.contractAddress,
                        srcAddress = srcAddress,
                        dstAddress = dstAddress,
                    )
                    val isApprovalRequired = allowance != null && allowance < srcTokenValue.value

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
                        estimatedTime = quote.estimatedTime,
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

                is SwapQuote.OneInch -> {
                    val dstAddress = quote.data.tx.to

                    val allowance = allowanceRepository.getAllowance(
                        chain = srcToken.chain,
                        contractAddress = srcToken.contractAddress,
                        srcAddress = srcAddress,
                        dstAddress = dstAddress,
                    )
                    val isApprovalRequired = allowance != null && allowance < srcTokenValue.value

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
                        estimatedTime = quote.estimatedTime,
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

            if (transaction.isApprovalRequired) {
                sendNavigator.navigate(
                    SendDst.VerifyApproval(
                        transactionId = transaction.id,
                    )
                )
            } else {
                sendNavigator.navigate(
                    SendDst.VerifyTransaction(
                        transactionId = transaction.id,
                    )
                )
            }
        }
    }

    fun selectSrcToken(model: TokenBalanceUiModel) {
        selectedSrc.value = model.model
    }

    fun selectDstToken(model: TokenBalanceUiModel) {
        selectedDst.value = model.model
    }

    fun flipSelectedTokens() {
        val buffer = selectedSrc.value
        selectedSrc.value = selectedDst.value
        selectedDst.value = buffer
    }

    fun loadData(vaultId: String, chainId: String?) {
        this.vaultId = vaultId
        loadTokens(vaultId, chainId)
    }

    fun validateAmount() {
        val errorMessage = validateSrcAmount(srcAmountState.text.toString())
        uiState.update { it.copy(amountError = errorMessage) }
    }

    private fun loadTokens(vaultId: String, chainId: String?) {
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
                    selectedSrc.updateSrc(addresses, chain)
                    selectedDst.updateSrc(addresses, chain)
                    updateUiTokens(
                        addresses
                            .asSequence()
                            .map { address ->
                                address.accounts.map {
                                    accountToTokenBalanceUiModelMapper.map(SendSrc(address, it))
                                }
                            }
                            .flatten()
                            .toList()
                    )
                }
        }
    }

    private fun updateUiTokens(tokenUiModels: List<TokenBalanceUiModel>) {
        uiState.update {
            it.copy(
                availableTokens = tokenUiModels,
            )
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
                        } ?: "0$"

                        // THORChain for cross-chain swap, 1inch for same-chain swap
                        if (srcToken.chain != dstToken.chain) {
                            val quote = swapQuoteRepository.getSwapQuote(
                                dstAddress = dst.address.address,
                                srcToken = srcToken,
                                dstToken = dstToken,
                                tokenValue = tokenValue,
                            )
                            this@SwapFormViewModel.quote = quote

                            val fiatFees =
                                convertTokenValueToFiat(dstToken, quote.fees, currency)

                            val estimatedTime = quote.estimatedTime?.let {
                                UiText.DynamicString(mapDurationToUiString(it))
                            } ?: R.string.swap_screen_estimated_time_instant.asUiText()

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
                                    provider = R.string.swap_form_provider_thorchain.asUiText(),
                                    srcFiatValue = srcFiatValueText,
                                    estimatedDstTokenValue = estimatedDstTokenValue,
                                    estimatedDstFiatValue = fiatValueToString.map(
                                        estimatedDstFiatValue
                                    ),
                                    fee = fiatValueToString.map(fiatFees),
                                    estimatedTime = estimatedTime,
                                )
                            }
                        } else {
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
                                value = quote.tx.gasPrice.toBigInteger() * EvmHelper.DefaultEthSwapGasUnit.toBigInteger(),
                                token = srcToken
                            )

                            this@SwapFormViewModel.quote = SwapQuote.OneInch(
                                expectedDstValue = expectedDstValue,
                                fees = tokenFees,
                                estimatedTime = null,
                                data = quote
                            )

                            val fiatFees =
                                convertTokenValueToFiat(dstToken, tokenFees, currency)

                            val estimatedTime =
                                R.string.swap_screen_estimated_time_instant.asUiText()

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
                                    estimatedTime = estimatedTime,
                                )
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

    companion object {

        private const val AFFILIATE_FEE_USD_THRESHOLD = 100

    }

}


internal fun MutableStateFlow<SendSrc?>.updateSrc(
    addresses: List<Address>,
    chain: Chain?,
) {
    val selectedSrcValue = value
    value = if (selectedSrcValue == null) {
        addresses.firstSendSrc(chain)
    } else {
        addresses.findCurrentSrc(selectedSrcValue)
    }
}

internal fun List<Address>.firstSendSrc(
    filterByChain: Chain?,
): SendSrc {
    val address = if (filterByChain == null) first()
    else first { it.chain.id == filterByChain.id }

    val account = if (filterByChain == null)
        address.accounts.first()
    else address.accounts.first { it.token.isNativeToken }

    return SendSrc(address, account)
}

internal fun List<Address>.findCurrentSrc(
    currentSrc: SendSrc,
): SendSrc {
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
}