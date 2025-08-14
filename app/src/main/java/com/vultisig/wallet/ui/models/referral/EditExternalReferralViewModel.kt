package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
    private val referralCodeRepository: ReferralCodeSettingsRepository,
    private val thorChainApi: ThorChainApi,
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
                referralCodeRepository.getExternalReferralBy(vaultId)
            } ?: ""

            referralCodeTextFieldState.setTextAndPlaceCursorAtEnd(referralCode)
        }
    }

    fun onSaveReferral() {
        viewModelScope.launch {
            val referralCode = referralCodeTextFieldState.text.toString().trim()
            validateReferralCode(referralCode)?.let { validationError ->
                state.update {
                    it.copy(
                        referralMessage = validationError,
                        referralMessageState = VsTextInputFieldInnerState.Error,
                    )
                }
                return@launch
            }
            checkAndSaveReferredCode(referralCode)
        }
    }

    private suspend fun checkAndSaveReferredCode(referralCode: String) {

        runCatching {
            withContext(Dispatchers.IO) {
                thorChainApi.existsReferralCode(referralCode)
            }
        }.onSuccess { exists ->
            val (message, innerState) = if (exists) {
                withContext(Dispatchers.IO) {
                    referralCodeRepository.saveExternalReferral(vaultId, referralCode)
                }
                "Referral code successfully linked" to VsTextInputFieldInnerState.Success
            } else {
                "Referral code does not exist" to VsTextInputFieldInnerState.Error
            }
            state.update {
                it.copy(
                    referralMessage = message,
                    referralMessageState = innerState,
                )
            }
        }.onFailure {
            state.update {
                it.copy(
                    referralMessage = "Failed to check referral code",
                    referralMessageState = VsTextInputFieldInnerState.Error,
                )
            }
        }
    }
}