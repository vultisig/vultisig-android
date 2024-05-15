package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text2.input.textAsFlow
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.GasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_CHAIN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

@Immutable
internal data class TokenBalanceUiModel(
    val model: Account,
    val title: String,
    val balance: String?,
    @DrawableRes val logo: Int,
)

@Immutable
internal data class SendUiModel(
    val selectedCoin: TokenBalanceUiModel? = null,
    val availableTokens: List<TokenBalanceUiModel> = emptyList(),
    val isTokensExpanded: Boolean = false,
    val from: String = "",
    val fiatCurrency: String = "",
    val fee: String? = null,
    val errorText: UiText? = null,
)

private data class InvalidTransactionDataException(
    val text: UiText,
) : Exception()

@OptIn(ExperimentalFoundationApi::class)
@HiltViewModel
internal class SendViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,

    private val vaultDb: VaultDB,
    private val accountsRepository: AccountsRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val gasFeeRepository: GasFeeRepository,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[ARG_VAULT_ID])

    private val chain: Chain = requireNotNull(savedStateHandle.get<String>(ARG_CHAIN_ID))
        .let(Chain::fromRaw)

    fun setAddressFromQrCode(qrCode: String?) {
        if (qrCode != null) {
            addressFieldState.setTextAndPlaceCursorAtEnd(qrCode)
        }
    }

    private val selectedAccount = MutableStateFlow<Account?>(null)
    private val appCurrency = appCurrencyRepository
        .currency
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            appCurrencyRepository.defaultCurrency,
        )

    private val gasFee = MutableStateFlow<GasFee?>(null)

    private var nativeTokenAccount: Account? = null

    private var lastToken = ""
    private var lastFiat = ""

    val addressFieldState = TextFieldState()
    val tokenAmountFieldState = TextFieldState()
    val fiatAmountFieldState = TextFieldState()

    val uiState = MutableStateFlow(SendUiModel())

    init {
        loadTokens()
        loadSelectedCurrency()
        collectSelectedAccount()
        collectAmountChanges()
        calculateGasFees()
    }

    fun selectToken(token: TokenBalanceUiModel) {
        selectedAccount.value = token.model
        toggleTokens()
    }

    fun toggleTokens() {
        uiState.update {
            it.copy(isTokensExpanded = !it.isTokensExpanded)
        }
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
        val selectedAccount = selectedAccount.value ?: return
        val selectedTokenValue = selectedAccount.tokenValue ?: return
        val gasFee = gasFee.value ?: return

        val max = if (selectedAccount.token.isNativeToken) {
            TokenValue(
                value = maxOf(BigInteger.ZERO, selectedTokenValue.value - gasFee.value.value),
                decimals = selectedTokenValue.decimals,
            )
        } else {
            selectedTokenValue
        }.decimal.toPlainString()

        tokenAmountFieldState.setTextAndPlaceCursorAtEnd(max)
    }

    fun dismissError() {
        uiState.update { it.copy(errorText = null) }
    }

    fun send() {
        viewModelScope.launch {
            try {
                val selectedAccount = selectedAccount.value
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_token)
                    )

                val gasFee = gasFee.value

                if (gasFee == null || gasFee.value.value <= BigInteger.ZERO) {
                    throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_gas_fee)
                    )
                }

                val address = addressFieldState.text.toString()

                if (address.isBlank() || !chainAccountAddressRepository.isValid(chain, address)) {
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
                    if (tokenAmountInt + gasFee.value.value > selectedTokenValue.value) {
                        throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_insufficient_balance)
                        )
                    }
                } else {
                    val nativeTokenValue = nativeTokenAccount?.tokenValue?.value
                        ?: throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_token)
                        )

                    if (selectedTokenValue.value < tokenAmountInt
                        || nativeTokenValue < gasFee.value.value
                    ) {
                        throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_insufficient_balance)
                        )
                    }
                }

                navigator.navigate(
                    Destination.VerifyTransaction(
                        vaultId = vaultId,
                        chainId = chain.raw,
                        tokenId = selectedToken.id,
                        dstAddress = address,
                        amount = tokenAmountInt.toString(),
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
        viewModelScope.launch {
            val vault = requireNotNull(vaultDb.select(vaultId))

            val address = chainAccountAddressRepository.getAddress(chain, vault)

            accountsRepository.loadChainAccounts(
                vaultId = vaultId,
                chain = chain,
            ).collect { accounts ->
                val tokenUiModels = accounts
                    .map(accountToTokenBalanceUiModelMapper::map)

                val accountOfNativeToken = accounts.find { it.token.isNativeToken }
                val selectedAccountValue = selectedAccount.value
                // so it doesnt reset user selection of token on update
                if (selectedAccountValue == null ||
                    selectedAccountValue.token.ticker == accountOfNativeToken?.token?.ticker
                ) {
                    selectedAccount.value = accountOfNativeToken
                }
                nativeTokenAccount = accountOfNativeToken

                uiState.update {
                    it.copy(
                        from = address,
                        availableTokens = tokenUiModels,
                    )
                }
            }
        }
    }

    private fun calculateGasFees() {
        viewModelScope.launch {
            try {
                val gasFee = gasFeeRepository.getGasFee(chain)

                this@SendViewModel.gasFee.value = gasFee

                uiState.update {
                    it.copy(fee = "${gasFee.value.decimal.toPlainString()} ${gasFee.unit}")
                }
            } catch (e: Throwable) {
                // TODO handle error when querying gas fee
                Timber.e(e)
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
            selectedAccount.collect { selectedAccount ->
                val uiModel = selectedAccount
                    ?.let(accountToTokenBalanceUiModelMapper::map)
                uiState.update {
                    it.copy(selectedCoin = uiModel)
                }
            }
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
            val selectedToken = selectedAccount.value?.token
                ?: return null

            val price = try {
                tokenPriceRepository.getPrice(
                    selectedToken.priceProviderID,
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

}

