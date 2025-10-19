package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.models.referral.ReferralViewModel.Companion.MAX_LENGTH_REFERRAL_CODE
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class EditExternalReferralUiState(
    val referralMessage: UiText? = null,
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
        observeReferralTextField()
    }

    private fun observeReferralTextField() {
        viewModelScope.launch {
            referralCodeTextFieldState
                .textAsFlow()
                .onEach {
                    if (state.value.referralMessage != null) {
                        if (it.length <= MAX_LENGTH_REFERRAL_CODE) {
                            state.update { current ->
                                current.copy(
                                    referralMessage = null,
                                    referralMessageState = VsTextInputFieldInnerState.Default,
                                )
                            }
                        }
                    }
                }
                .collect {}
        }
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
            validateMaxReferral(referralCode)?.let { validationError ->
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
        if (referralCode.isEmpty()) {
            withContext(Dispatchers.IO) {
                referralCodeRepository.saveExternalReferral(vaultId, null)
            }
            state.update {
                it.copy(
                    referralMessage = R.string.referral_code_removed_successfully.asUiText(),
                    referralMessageState = VsTextInputFieldInnerState.Success,
                )
            }
            return
        }

        runCatching {
            withContext(Dispatchers.IO) {
                thorChainApi.existsReferralCode(referralCode)
            }
        }.onSuccess { exists ->
            val (message, innerState) = if (exists) {
                withContext(Dispatchers.IO) {
                    referralCodeRepository.saveExternalReferral(vaultId, referralCode)
                }
                R.string.referral_code_linked_successfully.asUiText() to VsTextInputFieldInnerState.Success
            } else {
                R.string.referral_code_does_not_exist.asUiText() to VsTextInputFieldInnerState.Error
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
                    referralMessage = R.string.failed_to_check_referral_code.asUiText(),
                    referralMessageState = VsTextInputFieldInnerState.Error,
                )
            }
        }
    }

    fun onPasteIconClick(content: String) {
        viewModelScope.launch {
            val trimmedContent = content.trim()
            referralCodeTextFieldState.setTextAndPlaceCursorAtEnd(trimmedContent)
            val validation = validateReferralCode(trimmedContent)
            if (validation != null) {
                state.update {
                    it.copy(
                        referralMessage = validation,
                        referralMessageState = VsTextInputFieldInnerState.Error,
                    )
                }
            }
        }
    }
}