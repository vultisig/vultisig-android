package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class ReferralUiState(
    val referralCode: String = "",
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isSaveEnabled: Boolean = true,
    val isCreateEnabled: Boolean = true,
)

@HiltViewModel
internal class ReferralViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val referralCodeRepository: ReferralCodeSettingsRepository,
) : ViewModel() {
    private val vaultId: String = ""

    val referralCodeTextFieldState = TextFieldState()
    val state = MutableStateFlow(ReferralUiState())

    init {
        loadStatus()
    }

    private fun loadStatus() {
        viewModelScope.launch {
            val vaultReferral = referralCodeRepository.getReferralCreatedBy(vaultId)
            val externalReferral = referralCodeRepository.getExternalReferralBy(vaultId)

            state.update {
                it.copy(
                    referralCode = externalReferral ?: "",
                    isLoading = false,
                    isSaveEnabled = externalReferral.isNullOrEmpty(),
                    isCreateEnabled = vaultReferral.isNullOrEmpty(),
                )
            }
        }
    }

    fun onCreateOrEditReferral() {

    }

    fun onSaveOrEditReferral() {

    }

    fun onPasteIconClick(content: String) {

    }

    private fun validateReferralCode(code: String): String? {
        if (code.isEmpty()) return "Referral code cannot be empty"
        if (code.length != 4) return "Referral code must be exactly 4 characters"
        return null
    }
}