package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.cosmos.NativeTxFeeRune
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCaseImpl
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toUnit
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel.Companion.BLOCKS_PER_YEAR
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel.Companion.DATE_FORMAT
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel.Companion.DEFAULT_BLOCK_FEES
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_EXPIRATION_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_REFERRAL_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wallet.core.jni.CoinType
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject


internal data class EditVaultReferralUiState(
    val referralCounter: Int = 0,
    val referralCostAmount: String = "",
    val referralCostFiat: String = "",
    val referralExpiration: String = "",
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

    val referralTexFieldState = TextFieldState()
    val state = MutableStateFlow(EditVaultReferralUiState())

    init {
        initData()
        calculateFees()
    }

    private fun initData() {
        viewModelScope.launch {
            referralTexFieldState.setTextAndPlaceCursorAtEnd(vaultReferralCode)

            state.update {
                it.copy(
                    referralExpiration = vaultReferralExpiration,
                )
            }

            nativeRuneFees = withContext(Dispatchers.IO) {
                thorChainApi.getTHORChainReferralFees()
            }
        }
    }

    private fun updateReferralCounter(yearsDelta: Long) {
        val formatter = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.getDefault())
        val stateValue = state.value

        val newExpiration = LocalDate.parse(stateValue.referralExpiration, formatter)
            .plusYears(yearsDelta)
            .format(formatter)

        val newCounter = stateValue.referralCounter + yearsDelta.toInt()

        state.update {
            it.copy(
                referralCounter = newCounter,
                referralExpiration = newExpiration,
            )
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
                        referralCostFiat = totalFeesFiat,
                        referralCostAmount = "0 ${CoinType.THORCHAIN.symbol}",
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
                    referralCostFiat = totalFeesFiat,
                    referralCostAmount = formattedRegistrationTokenFees,
                )
            }
        }
    }

    fun onSavedReferral() {

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
}