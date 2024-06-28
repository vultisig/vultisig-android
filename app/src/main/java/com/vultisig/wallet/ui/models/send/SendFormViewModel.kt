package com.vultisig.wallet.ui.models.send

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.clearText
import androidx.compose.foundation.text2.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text2.input.textAsFlow
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.models.AllowZeroGas
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.presenter.common.TextFieldUtils
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.swap.updateSrc
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject

@Immutable
internal data class TokenBalanceUiModel(
    val model: SendSrc,
    val title: String,
    val balance: String?,
    val isNativeToken: Boolean,
    val isLayer2: Boolean,
    val tokenStandard: String?,
    val tokenLogo: ImageModel,
    @DrawableRes val chainLogo: Int,
)

@Immutable
internal data class SendFormUiModel(
    val selectedCoin: TokenBalanceUiModel? = null,
    val availableTokens: List<TokenBalanceUiModel> = emptyList(),
    val from: String = "",
    val fiatCurrency: String = "",
    val fee: String? = null,
    val errorText: UiText? = null,
    val showGasFee: Boolean = true,
    val dstAddressError: UiText? = null,
    val tokenAmountError: UiText? = null,
    val hasMemo: Boolean = false,
)

internal data class SendSrc(
    val address: Address,
    val account: Account,
)

private data class InvalidTransactionDataException(
    val text: UiText,
) : Exception()

@OptIn(ExperimentalFoundationApi::class)
@HiltViewModel
internal class SendFormViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val sendNavigator: Navigator<SendDst>,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
    private val mapGasFeeToString: TokenValueToStringWithUnitMapper,

    private val accountsRepository: AccountsRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val gasFeeRepository: GasFeeRepository,
    private val transactionRepository: TransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
) : ViewModel() {

    private lateinit var vaultId: String
    private var chain: Chain? = null

    fun setAddressFromQrCode(qrCode: String?) {
        if (qrCode != null) {
            addressFieldState.setTextAndPlaceCursorAtEnd(qrCode)
        }
    }

    private val selectedSrc = MutableStateFlow<SendSrc?>(null)

    private val selectedAccount: Account?
        get() = selectedSrc.value?.account

    private val appCurrency = appCurrencyRepository
        .currency
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            appCurrencyRepository.defaultCurrency,
        )

    private val gasFee = MutableStateFlow<TokenValue?>(null)

    private val specific = MutableStateFlow<BlockChainSpecificAndUtxo?>(null)


    private var lastToken = ""
    private var lastFiat = ""

    val addressFieldState = TextFieldState()
    val tokenAmountFieldState = TextFieldState()
    val fiatAmountFieldState = TextFieldState()
    val memoFieldState: TextFieldState = TextFieldState()

    val uiState = MutableStateFlow(SendFormUiModel())

    fun loadData(vaultId: String, chainId: String?) {
        this.vaultId = vaultId
        this.chain = chainId?.let(Chain::fromRaw)
        loadTokens()
        loadSelectedCurrency()
        collectSelectedAccount()
        collectAmountChanges()
        calculateGasFees()
        calculateSpecific()
    }

    fun validateDstAddress() {
        val errorText = validateDstAddress(addressFieldState.text.toString())
        uiState.update {
            it.copy(dstAddressError = errorText)
        }
    }

    fun validateTokenAmount() {
        val errorText = validateTokenAmount(tokenAmountFieldState.text.toString())
        uiState.update { it.copy(tokenAmountError = errorText) }
    }

    fun selectToken(token: TokenBalanceUiModel) {
        selectedSrc.value = token.model
        memoFieldState.clearText()
    }

    fun setOutputAddress(address: String) {
        addressFieldState.setTextAndPlaceCursorAtEnd(address)
    }

    fun scanAddress() {
        viewModelScope.launch {
            navigator.navigate(Destination.ScanQr)
        }
    }

    fun chooseMaxTokenAmount() {
        val selectedAccount = selectedAccount ?: return
        val selectedTokenValue = selectedAccount.tokenValue ?: return
        val gasFee = gasFee.value ?: return
        val specific = specific.value ?: return
        val chain = selectedAccount.token.chain

        viewModelScope.launch {
            val max = if (selectedAccount.token.isNativeToken) {
                val gasLimit = if (chain.standard == TokenStandard.EVM) {
                    (specific.blockChainSpecific as BlockChainSpecific.Ethereum).gasLimit
                } else {
                    BigInteger.valueOf(1)
                }
                TokenValue(
                    value = maxOf(
                        BigInteger.ZERO,
                        selectedTokenValue.value - gasFee.value.multiply(gasLimit)
                    ),
                    unit = selectedTokenValue.unit,
                    decimals = selectedTokenValue.decimals,
                )
            } else {
                selectedTokenValue
            }.decimal.toPlainString()
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd(max)
        }
    }

    fun choosePercentageAmount(percentage: Float) {
        val selectedTokenValue = selectedAccount?.tokenValue ?: return

        val tokenValue = selectedTokenValue.copy(
            value = (BigDecimal(selectedTokenValue.value) * percentage.toBigDecimal()).toBigInteger(),
        )
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd(tokenValue.decimal.toPlainString())
    }

    fun dismissError() {
        uiState.update { it.copy(errorText = null) }
    }

    fun send() {
        viewModelScope.launch {
            try {
                val selectedAccount = selectedAccount
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_token)
                    )

                val chain = selectedAccount.token.chain

                val gasFee = gasFee.value
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_gas_fee)
                    )

                if (!selectedAccount.token.AllowZeroGas() && gasFee.value <= BigInteger.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_gas_fee)
                    )
                }
                val dstAddress = addressFieldState.text.toString()

                if (dstAddress.isBlank() ||
                    !chainAccountAddressRepository.isValid(chain, dstAddress)
                ) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )
                }

                val tokenAmount = tokenAmountFieldState.text
                    .toString()
                    .toBigDecimalOrNull()

                if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_amount)
                    )
                }

                val selectedTokenValue = selectedAccount.tokenValue
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_token)
                    )

                val selectedToken = selectedAccount.token

                val tokenAmountInt =
                    tokenAmount
                        .movePointRight(selectedToken.decimal)
                        .toBigInteger()

                if (selectedToken.isNativeToken) {
                    if (tokenAmountInt + gasFee.value > selectedTokenValue.value) {
                        throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_insufficient_balance)
                        )
                    }
                } else {
                    val nativeTokenAccount = selectedSrc.value?.address?.accounts
                        ?.find { it.token.isNativeToken }
                    val nativeTokenValue = nativeTokenAccount?.tokenValue?.value
                        ?: throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_token)
                        )

                    if (selectedTokenValue.value < tokenAmountInt
                        || nativeTokenValue < gasFee.value
                    ) {
                        throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_insufficient_balance)
                        )
                    }
                }

                val srcAddress = selectedToken.address

                val specific = blockChainSpecificRepository
                    .getSpecific(chain, srcAddress, selectedToken, gasFee, isSwap = false)

                val transaction = Transaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = vaultId,
                    chainId = chain.raw,
                    tokenId = selectedToken.id,
                    srcAddress = srcAddress,
                    dstAddress = dstAddress,
                    tokenValue = TokenValue(
                        value = tokenAmountInt,
                        unit = selectedTokenValue.unit,
                        decimals = selectedToken.decimal,
                    ),
                    fiatValue = FiatValue(
                        value = fiatAmountFieldState.text.toString().toBigDecimal(),
                        currency = appCurrency.value.ticker,
                    ),
                    gasFee = gasFee,

                    blockChainSpecific = specific.blockChainSpecific,
                    utxos = specific.utxos,
                    memo = memoFieldState.text.toString().takeIf { it.isNotEmpty() },
                )

                Timber.d("Transaction: $transaction")

                transactionRepository.addTransaction(transaction)

                sendNavigator.navigate(
                    SendDst.VerifyTransaction(
                        transactionId = transaction.id,
                    )
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            }
        }
    }

    private fun showError(text: UiText) {
        uiState.update { it.copy(errorText = text) }
    }

    private fun loadTokens() {
        val chain = chain
        viewModelScope.launch {
            accountsRepository.loadAddresses(vaultId)
                .catch {
                    // TODO handle error
                    Timber.e(it)
                }.collect { addresses ->
                    selectedSrc.updateSrc(addresses, chain)

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

    private fun calculateGasFees() {
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
                    this@SendFormViewModel.gasFee.value = gasFee

                    uiState.update {
                        it.copy(fee = mapGasFeeToString(gasFee))
                    }
                }
        }
    }

    private fun calculateSpecific() {
        viewModelScope.launch {
            selectedSrc.filterNotNull().combine(gasFee.filterNotNull()) { selectedSrc, gasFee ->
                val selectedAccount = selectedSrc.account
                val chain = selectedAccount.token.chain
                val selectedToken = selectedAccount.token
                val srcAddress = selectedAccount.token.address
                blockChainSpecificRepository
                    .getSpecific(chain, srcAddress, selectedToken, gasFee, isSwap = false)
            }.collect {
                specific.value = it
            }
        }
    }

    private fun loadSelectedCurrency() {
        viewModelScope.launch {
            appCurrency.collect { appCurrency ->
                uiState.update {
                    it.copy(fiatCurrency = appCurrency.ticker)
                }
            }
        }
    }

    private fun collectSelectedAccount() {
        viewModelScope.launch {
            selectedSrc
                .filterNotNull()
                .map { src ->
                    val address = src.address.address
                    val uiModel = accountToTokenBalanceUiModelMapper.map(src)
                    val showGasFee = (selectedAccount?.token?.AllowZeroGas() == false)
                    val hasMemo = src.account.token.isNativeToken
                    uiState.update {
                        it.copy(
                            from = address,
                            selectedCoin = uiModel,
                            showGasFee = showGasFee,
                            hasMemo = hasMemo,
                        )
                    }
                }.collect()
        }
    }

    private fun collectAmountChanges() {
        viewModelScope.launch {
            combine(
                tokenAmountFieldState.textAsFlow(),
                fiatAmountFieldState.textAsFlow(),
            ) { tokenFieldValue, fiatFieldValue ->
                val tokenString = tokenFieldValue.toString()
                val fiatString = fiatFieldValue.toString()
                if (lastToken != tokenString) {
                    val fiatValue = convertValue(tokenString) { value, price, token ->
                        value.multiply(price)
                    } ?: return@combine

                    lastToken = tokenString
                    lastFiat = fiatValue

                    fiatAmountFieldState.setTextAndPlaceCursorAtEnd(fiatValue)
                } else if (lastFiat != fiatString) {
                    val tokenValue = convertValue(fiatString) { value, price, token ->
                        value.divide(price, token.decimal, RoundingMode.HALF_UP)
                    } ?: return@combine

                    lastToken = tokenValue
                    lastFiat = fiatString

                    tokenAmountFieldState.setTextAndPlaceCursorAtEnd(tokenValue)
                }
            }.collect()
        }
    }

    private suspend fun convertValue(
        value: String,
        transform: (
            value: BigDecimal,
            price: BigDecimal,
            token: Coin,
        ) -> BigDecimal,
    ): String? {
        val decimalValue = value.toBigDecimalOrNull()

        return if (decimalValue != null) {
            val selectedToken = selectedAccount?.token
                ?: return null

            val price = try {
                tokenPriceRepository.getPrice(
                    selectedToken,
                    appCurrency.value,
                ).first()
            } catch (e: Exception) {
                Timber.d("Failed to get price for token $selectedToken")
                return null
            }

            transform(decimalValue, price, selectedToken)
                .toPlainString()
        } else {
            ""
        }
    }

    private fun validateDstAddress(dstAddress: String): UiText? {
        val selectedAccount = selectedAccount
            ?: return UiText.StringResource(R.string.send_error_no_token)
        val chain = selectedAccount.token.chain
        if (dstAddress.isBlank() || !chainAccountAddressRepository.isValid(chain, dstAddress))
            return UiText.StringResource(R.string.send_error_no_address)
        return null
    }

    private fun validateTokenAmount(tokenAmount: String): UiText? {
        if (tokenAmount.length > TextFieldUtils.AMOUNT_MAX_LENGTH)
            return UiText.StringResource(R.string.send_from_invalid_amount)
        val tokenAmountBigDecimal = tokenAmount.toBigDecimalOrNull()
        if (tokenAmountBigDecimal == null || tokenAmountBigDecimal <= BigDecimal.ZERO) {
            return UiText.StringResource(R.string.send_error_no_amount)
        }
        return null
    }

}

