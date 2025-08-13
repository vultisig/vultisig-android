package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class EditExternalReferralUiState(
    val referralMessage: String? = null,
    val referralMessageState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
)

@HiltViewModel
internal class EditExternalReferralViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val referralCodeSettingsRepository: ReferralCodeSettingsRepository
) : ViewModel() {
    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])

    val referralCodeTextFieldState = TextFieldState()
    val state = MutableStateFlow(EditExternalReferralUiState())

    init {
        prefillReferral()
    }

    private fun prefillReferral() {
        viewModelScope.launch {
            val referralCode = withContext(Dispatchers.IO) {
                referralCodeSettingsRepository.getExternalReferralBy(vaultId)
            } ?: ""

            referralCodeTextFieldState.setTextAndPlaceCursorAtEnd(referralCode)
        }
    }

    fun onSaveReferral() {

    }
}