package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.textAsFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.common.asUiText
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.IsSwapSupported
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
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
import java.util.UUID
import javax.inject.Inject

internal data class SwapFormUiModel(
    val selectedSrcToken: TokenBalanceUiModel? = null,
    val selectedDstToken: TokenBalanceUiModel? = null,
    val availableTokens: List<TokenBalanceUiModel> = emptyList(),
    val estimatedDstTokenValue: String = "",
    val gas: String = "",
    val fee: String = "",
    val estimatedTime: UiText = UiText.DynamicString(""),
)

@OptIn(ExperimentalFoundationApi::class)
@HiltViewModel
internal class SwapFormViewModel @Inject constructor(
    private val sendNavigator: Navigator<SendDst>,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
    private val mapTokenValueToString: TokenValueToStringWithUnitMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToString: FiatValueToStringMapper,

    private val convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase,
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

        // TODO reuse this with calculateFees
        val srcTokenValue = srcAmountState.text.toString()
            .toBigDecimalOrNull()
            ?.movePointRight(selectedSrc.account.token.decimal)
            ?.toBigInteger()
            ?.let { convertTokenAndValueToTokenValue(srcToken, it) }
            ?: return


        viewModelScope.launch {
            // TODO cache last quote
            val quote = swapQuoteRepository.getSwapQuote(
                dstAddress = selectedDst.address.address,
                srcToken = srcToken,
                dstToken = dstToken,
                tokenValue = srcTokenValue,
            )

            val dstTokenValue = quote.expectedDstValue

            val specificAndUtxo = blockChainSpecificRepository.getSpecific(
                srcToken.chain,
                srcAddress,
                srcToken,
                gasFee,
                isSwap = true,
            )

            val transaction = SwapTransaction(
                id = UUID.randomUUID().toString(),
                vaultId = vaultId,
                srcToken = srcToken,
                srcTokenValue = srcTokenValue,
                srcAddress = srcAddress,
                dstToken = dstToken,
                dstAddress = quote.routerAddress ?: quote.inboundAddress ?: srcAddress,
                expectedDstTokenValue = dstTokenValue,
                blockChainSpecific = specificAndUtxo,
                vaultAddress = quote.inboundAddress ?: srcAddress,
                routerAddress = quote.routerAddress,
            )

            swapTransactionRepository.addTransaction(transaction)

            sendNavigator.navigate(
                SendDst.Keysign(
                    transactionId = transaction.id,
                )
            )
        }

//        viewModelScope.launch {
//            sendNavigator.navigate(
//                SendDst.VerifyTransaction(
//                    transactionId = "transactionId",
//                )
//            )
//        }
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

                    val srcTokenValue = amount
                        ?.movePointRight(src.account.token.decimal)
                        ?.toBigInteger()

                    try {
                        val tokenValue = srcTokenValue?.let {
                            convertTokenAndValueToTokenValue(srcToken, srcTokenValue)
                            // todo currently ?: option is to get quotes
                            //  if user didn't input any value. can we do it better?
                        } ?: TokenValue(
                            1_000_000_000.toBigInteger(),
                            srcToken.ticker,
                            srcToken.decimal
                        )

                        val quote = swapQuoteRepository.getSwapQuote(
                            dstAddress = dst.address.address,
                            srcToken = src.account.token,
                            dstToken = dst.account.token,
                            tokenValue = tokenValue,
                        )

                        val currency = appCurrencyRepository.currency.first()
                        val fiatFees =
                            convertTokenValueToFiat(dst.account.token, quote.fees, currency)

                        // todo convert seconds to human readable format
                        val estimatedTime = quote.estimatedTime?.toString()?.let {
                            UiText.DynamicString(it)
                        } ?: R.string.swap_screen_estimated_time_instant.asUiText()

                        uiState.update {
                            it.copy(
                                estimatedDstTokenValue = mapTokenValueToDecimalUiString(
                                    quote.expectedDstValue
                                ),
                                fee = fiatValueToString.map(fiatFees),
                                estimatedTime = estimatedTime,
                            )
                        }
                    } catch (e: Exception) {
                        // TODO handle error
                        Timber.e(e)
                    }
                }
        }
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