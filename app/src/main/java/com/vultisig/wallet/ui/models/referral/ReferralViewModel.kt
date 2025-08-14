package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class ReferralUiState(
    val referralCode: String = "",
    val referralMessage: String? = null,
    val referralMessageState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
    val isLoading: Boolean = false,
    val isSaveEnabled: Boolean = true,
    val isCreateEnabled: Boolean = true,
)

@HiltViewModel
internal class ReferralViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val referralCodeRepository: ReferralCodeSettingsRepository,
    private val thorChainApi: ThorChainApi,
) : ViewModel() {
    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])

    val referralCodeTextFieldState = TextFieldState()
    val state = MutableStateFlow(ReferralUiState())

    init {
        loadStatus()
        observeReferralTextField()
    }

    private fun observeReferralTextField() {
        viewModelScope.launch {
            referralCodeTextFieldState
                .textAsFlow()
                .onEach {
                    if (state.value.referralMessage != null) {
                        if (it.length in MIN_LENGTH_REFERRAL_CODE..MAX_LENGTH_REFERRAL_CODE) {
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

    private fun loadStatus() {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true) }

            val (vaultReferral, externalReferral) = withContext(Dispatchers.IO) {
                referralCodeRepository.getReferralCreatedBy(vaultId) to
                        referralCodeRepository.getExternalReferralBy(vaultId)
            }

            state.update {
                it.copy(
                    referralCode = externalReferral ?: "",
                    isLoading = false,
                    isSaveEnabled = externalReferral.isNullOrEmpty(),
                    isCreateEnabled = vaultReferral.isNullOrEmpty(),
                )
            }
            if (!externalReferral.isNullOrEmpty()) {
                referralCodeTextFieldState.setTextAndPlaceCursorAtEnd(externalReferral)
            }
        }
    }

    fun onCreateOrEditReferral() {
        viewModelScope.launch {
            if (state.value.isCreateEnabled) {
                navigator.navigate(Destination.ReferralCreation(vaultId))
            } else {
                // navigate to visit referral
            }
        }
    }

    fun onSaveOrEditExternalReferral() {
        viewModelScope.launch {
            if (state.value.isSaveEnabled) {
                state.update {
                    it.copy(
                        referralMessageState = VsTextInputFieldInnerState.Default,
                    )
                }
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
            } else {
                navigator.navigate(Destination.ReferralExternalEdition(vaultId))
            }
        }
    }

    private suspend fun checkAndSaveReferredCode(referralCode: String) {
        state.update { it.copy(isLoading = true) }

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
            val isSavedEnabled = innerState != VsTextInputFieldInnerState.Success

            state.update {
                it.copy(
                    referralMessage = message,
                    referralMessageState = innerState,
                    isSaveEnabled = isSavedEnabled,
                    isLoading = false,
                )
            }
        }.onFailure {
            state.update {
                it.copy(
                    referralMessage = "Failed to check referral code",
                    referralMessageState = VsTextInputFieldInnerState.Error,
                    isLoading = false,
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

    fun onNewEditedReferral(newEditedReferral: String) {
        referralCodeTextFieldState.setTextAndPlaceCursorAtEnd(newEditedReferral)
    }

    internal companion object {
        const val MAX_LENGTH_REFERRAL_CODE = 4
        const val MIN_LENGTH_REFERRAL_CODE = 1
    }
}