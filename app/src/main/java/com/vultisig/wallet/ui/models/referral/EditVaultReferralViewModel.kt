package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCaseImpl
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_EXPIRATION_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_REFERRAL_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class EditVaultReferralUiState(
    val referralCounter: String = "0",
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
) : ViewModel() {
    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])
    private val vaultReferralCode: String = requireNotNull(savedStateHandle[ARG_REFERRAL_ID])
    private val vaultReferralExpiration: String = requireNotNull(savedStateHandle[ARG_EXPIRATION_ID])

    val referralTexFieldState = TextFieldState()
    val state = MutableStateFlow(EditVaultReferralUiState())

    init {
        initData()
    }

    private fun initData() {
        viewModelScope.launch {
            referralTexFieldState.setTextAndPlaceCursorAtEnd(vaultReferralCode)

            state.update {
                it.copy(
                    referralExpiration = vaultReferralExpiration,
                )
            }
        }
    }

    fun onSavedReferral() {

    }
}