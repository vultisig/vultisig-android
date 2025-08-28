package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.cosmos.NativeTxFeeRune
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCaseImpl
import com.vultisig.wallet.data.utils.decimals
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel.Companion.BLOCKS_PER_YEAR
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel.Companion.DATE_FORMAT
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel.Companion.DEFAULT_BLOCK_FEES
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_EXPIRATION_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_REFERRAL_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.CoinType
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject


internal data class EditVaultReferralUiState(
    val referralCounter: Int = 0,
    val referralCostAmountFormatted: String = "",
    val referralCostFiatFormatted: String = "",
    val referralExpiration: String = "",
    val costFeesTokenAmount: String = "",
    val error: ReferralError? = null,
)

@HiltViewModel
internal class EditVaultReferralViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val gasFeeToEstimate: GasFeeToEstimatedFeeUseCaseImpl,
    private val accountsRepository: AccountsRepository,
    private val transactionRepository: DepositTransactionRepository,
    private val thorChainApi: ThorChainApi,
) : ViewModel() {
    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])
    private val vaultReferralCode: String = requireNotNull(savedStateHandle[ARG_REFERRAL_ID])
    private val vaultReferralExpiration: String =
        requireNotNull(savedStateHandle[ARG_EXPIRATION_ID])
    private var nativeRuneFees: NativeTxFeeRune? = null

    val referralTextFieldState = TextFieldState()
    val state = MutableStateFlow(EditVaultReferralUiState())
    private var address: Address? = null

    init {
        loadAddress()
        initData()
        calculateFees()
    }

    private fun initData() {
        viewModelScope.launch {
            referralTextFieldState.setTextAndPlaceCursorAtEnd(vaultReferralCode)

            state.update {
                it.copy(
                    referralExpiration = vaultReferralExpiration,
                )
            }

            try {
                nativeRuneFees = withContext(Dispatchers.IO) {
                    thorChainApi.getTHORChainReferralFees()
                }
            } catch (e: Exception) {
                Timber.w(e, "Falling back to default referral fees")
                nativeRuneFees = null
            }
        }
    }

    private fun updateReferralCounter(yearsDelta: Long) {
        try {
            val formatter = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.getDefault())
            val stateValue = state.value

            val newExpiration = LocalDate.parse(stateValue.referralExpiration, formatter)
                .plusYears(yearsDelta)
                .format(formatter)

            val newCounter = (stateValue.referralCounter + yearsDelta.toInt()).coerceAtLeast(0)

            state.update {
                it.copy(
                    referralCounter = newCounter,
                    referralExpiration = newExpiration,
                )
            }
        } catch (t: Throwable) {
            Timber.e(t, "Failed to parse date")
        }
    }

    fun onIncrementCounter() {
        viewModelScope.launch {
            updateReferralCounter(yearsDelta = 1)
            calculateFees()
        }
    }

    fun onDecrementCounter() {
        viewModelScope.launch {
            updateReferralCounter(yearsDelta = -1)
            calculateFees()
        }
    }

    private fun calculateFees() {
        viewModelScope.launch {
            val years = state.value.referralCounter

            if (years == 0) {
                val totalFeesFiat = withContext(Dispatchers.IO) {
                    BigInteger.ZERO.convertToFiat()
                }
                state.update {
                    it.copy(
                        referralCostFiatFormatted = totalFeesFiat,
                        referralCostAmountFormatted = "0 ${CoinType.THORCHAIN.symbol}",
                        costFeesTokenAmount = "0"
                    )
                }
                return@launch
            }

            val totalFees =
                (nativeRuneFees?.feePerBlock
                    ?: DEFAULT_BLOCK_FEES).toBigInteger() * years.toBigInteger() * BLOCKS_PER_YEAR

            val totalFeesFiat = withContext(Dispatchers.IO) {
                totalFees.convertToFiat()
            }

            val formattedRegistrationTokenFees =
                "${CoinType.THORCHAIN.toValue(totalFees)} ${CoinType.THORCHAIN.symbol}"

            state.update {
                it.copy(
                    costFeesTokenAmount = totalFees.toString(),
                    referralCostFiatFormatted = totalFeesFiat,
                    referralCostAmountFormatted = formattedRegistrationTokenFees,
                )
            }
        }
    }

    fun onSavedReferral() {
        viewModelScope.launch {
            try {
                val account = address?.accounts?.find { it.token.isNativeToken }
                    ?: error("Can't load account")
                val balance = account.tokenValue?.value ?: BigInteger.ZERO
                val totalFees = state.value.costFeesTokenAmount.toBigInteger()
                val gasFeeValue = nativeRuneFees?.value?.toBigInteger() ?: "2000000".toBigInteger()
                if (balance < totalFees + gasFeeValue) {
                    state.update {
                        it.copy(error = ReferralError.BALANCE_ERROR)
                    }
                    return@launch
                }

                val address = account.token.address
                val memo = "~:${vaultReferralCode.uppercase()}:THOR:$address:$address"
                val gasFees = TokenValue(
                    value = gasFeeValue,
                    unit = CoinType.THORCHAIN.symbol,
                    decimals = CoinType.THORCHAIN.decimals,
                )
                val toAmount = state.value.costFeesTokenAmount.toBigInteger()
                val blockchainSpecific = withContext(Dispatchers.IO) {
                    blockChainSpecificRepository.getSpecific(
                        chain = Chain.ThorChain,
                        address = account.token.address,
                        token = account.token,
                        isDeposit = true,
                        memo = memo,
                        isSwap = false,
                        isMaxAmountEnabled = false,
                        transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                        tokenAmountValue = state.value.costFeesTokenAmount.toBigInteger(),
                        gasFee = gasFees,
                    ).blockChainSpecific
                }

                val tx = DepositTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = vaultId,
                    srcToken = account.token,
                    srcAddress = address,
                    dstAddress = "",
                    memo = memo,
                    srcTokenValue = TokenValue(
                        value = toAmount,
                        token = account.token,
                    ),
                    estimatedFees = gasFees,
                    estimateFeesFiat = withContext(Dispatchers.IO){ gasFees.value.convertToFiat() },
                    blockChainSpecific = blockchainSpecific,
                )

                transactionRepository.addTransaction(tx)

                navigator.route(
                    Route.VerifyDeposit(vaultId, tx.id)
                )
            } catch (t: Throwable) {
                Timber.e(t, "Failed to save edited referral")
                state.update {
                    it.copy(error = ReferralError.UNKNOWN_ERROR)
                }
            }
        }
    }

    private fun loadAddress() {
        viewModelScope.launch {
            try {
                accountsRepository.loadAddress(vaultId, Chain.ThorChain)
                    .collect { address ->
                        this@EditVaultReferralViewModel.address = address
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load address")
                Timber.e(e)
            }
        }
    }

    private suspend fun BigInteger.convertToFiat(): String {
        val gasFeeParams = Coins.coins[Chain.ThorChain]?.first()?.let { selectedCoin ->
            GasFeeParams(
                gasLimit = BigInteger.ONE,
                gasFee = TokenValue(this, "RUNE", 8),
                selectedToken = selectedCoin,
            )
        } ?: error("Can't calculate fees")

        return gasFeeToEstimate.invoke(gasFeeParams).formattedFiatValue
    }

    fun onDismissError() {
        viewModelScope.launch {
            state.update {
                it.copy(error = null)
            }
        }
    }
}