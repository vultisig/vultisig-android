@file:OptIn(ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.models.deposit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.common.asUiText
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.models.AllowZeroGas
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.presenter.common.TextFieldUtils
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject

@Immutable
internal data class DepositFormUiModel(
    val depositMessage: UiText = UiText.Empty,
    val depositOption: String = "Bond",
    val depositOptions: List<String> = listOf(
        "Bond",
        // TODO options
//        "Unbond",
//        "Leave",
//        "Custom",
    ),
    val errorText: UiText? = null,
    val tokenAmountError: UiText? = null,
    val nodeAddressError: UiText? = null,
    val providerError: UiText? = null,
    val operatorFeeError: UiText? = null,
)

@HiltViewModel
internal class DepositFormViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val sendNavigator: Navigator<SendDst>,

    private val gasFeeRepository: GasFeeRepository,
    private val accountsRepository: AccountsRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val transactionRepository: DepositTransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
) : ViewModel() {

    private lateinit var vaultId: String
    private var chain: Chain? = null

    fun setAddressFromQrCode(vaultId: String, qrCode: String?) {
        if (qrCode != null) {
            nodeAddressFieldState.setTextAndPlaceCursorAtEnd(qrCode)
            Chain.entries.find { chain ->
                chainAccountAddressRepository.isValid(chain, qrCode)
            }?.let { chain ->
                loadData(vaultId, chain.id)
            }
        }
    }

    val tokenAmountFieldState = TextFieldState()
    val nodeAddressFieldState = TextFieldState()
    val providerFieldState = TextFieldState()
    val operatorFeeFieldState = TextFieldState()

    val state = MutableStateFlow(DepositFormUiModel())

    init {

    }

    fun loadData(
        vaultId: String,
        chainId: String,
    ) {
        this.vaultId = vaultId
        val chain = chainId.let(Chain::fromRaw)
        this.chain = chain

        state.update {
            it.copy(
                depositMessage = R.string.deposit_message_deposit_title.asUiText(chain.raw)
            )
        }
    }

    fun selectDepositOption(option: String) {
        state.update { it.copy(depositOption = option) }
    }

    fun validateNodeAddress() {
        val errorText = validateDstAddress(nodeAddressFieldState.text.toString())
        state.update {
            it.copy(nodeAddressError = errorText)
        }
    }

    fun validateTokenAmount() {
        val errorText = validateTokenAmount(tokenAmountFieldState.text.toString())
        state.update { it.copy(tokenAmountError = errorText) }
    }

    fun validateProvider() {
        val errorText = validateDstAddress(providerFieldState.text.toString())
        state.update {
            it.copy(providerError = errorText)
        }
    }

    fun validateOperatorFee() {
        val errorText = validateTokenAmount(operatorFeeFieldState.text.toString())
        state.update { it.copy(operatorFeeError = errorText) }
    }

    fun setProvider(provider: String) {
        providerFieldState.setTextAndPlaceCursorAtEnd(provider)
    }

    fun setNodeAddress(address: String) {
        nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address)
    }

    fun scan() {
        viewModelScope.launch {
            navigator.navigate(Destination.ScanQr)
        }
    }

    fun dismissError() {
        state.update { it.copy(errorText = null) }
    }

    fun deposit() {
        viewModelScope.launch {
            try {
                val chain = chain
                    ?: throw InvalidTransactionDataException(
                        UiText.StringResource(R.string.send_error_no_address)
                    )

                val nodeAddress = nodeAddressFieldState.text.toString()

                if (nodeAddress.isBlank() ||
                    !chainAccountAddressRepository.isValid(chain, nodeAddress)
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

                val operatorFeeAmount = operatorFeeFieldState.text
                    .toString()
                    .toBigDecimalOrNull()

                val address = accountsRepository.loadAddress(vaultId, chain)
                    .first()

                val selectedToken = address.accounts.first { it.token.isNativeToken }.token

                val tokenAmountInt =
                    tokenAmount
                        .movePointRight(selectedToken.decimal)
                        .toBigInteger()

                val operatorFeeTokenValue = operatorFeeAmount?.let {
                    TokenValue(
                        value = it.movePointRight(selectedToken.decimal)
                            .toBigInteger(),
                        token = selectedToken,
                    )
                }

                val srcAddress = selectedToken.address

                val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

                val providerText = providerFieldState.text.toString()
                val provider = if (providerText.isNotBlank()) providerText else null

                val memo = DepositMemo.Bond(
                    nodeAddress = nodeAddress,
                    providerAddress = provider,
                    operatorFee = operatorFeeTokenValue,
                )

                val specific = blockChainSpecificRepository
                    .getSpecific(chain, srcAddress, selectedToken, gasFee, isSwap = false)

                val transaction = DepositTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = vaultId,

                    srcToken = selectedToken,
                    srcAddress = srcAddress,
                    dstAddress = nodeAddress,

                    memo = memo.toString(),
                    srcTokenValue = TokenValue(
                        value = tokenAmountInt,
                        token = selectedToken,
                    ),
                    estimatedFees = gasFee,
                    blockChainSpecific = specific.blockChainSpecific,
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
        state.update { it.copy(errorText = text) }
    }

    private fun validateDstAddress(dstAddress: String): UiText? {
        val chain = chain ?: return UiText.StringResource(R.string.dialog_default_error_title)
        if (dstAddress.isBlank() || !chainAccountAddressRepository.isValid(chain, dstAddress))
            return UiText.StringResource(R.string.send_error_no_address)
        return null
    }

    private fun validateTokenAmount(tokenAmount: String): UiText? {
        if (tokenAmount.length > TextFieldUtils.AMOUNT_MAX_LENGTH)
            return UiText.StringResource(R.string.send_from_invalid_amount)
        val tokenAmountBigDecimal = tokenAmount.toBigDecimalOrNull()
        if (tokenAmountBigDecimal == null || tokenAmountBigDecimal < BigDecimal.ZERO) {
            return UiText.StringResource(R.string.send_error_no_amount)
        }
        return null
    }

}

