package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.ui.navigation.Destination
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
    private val thorChainApi: ThorChainApi,
) : ViewModel() {
    private val vaultId: String = ""

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
                    if (state.value.errorMessage != null) {
                        if (it.length in 1..4) {
                            state.update { current ->
                                current.copy(errorMessage = null)
                            }
                        }
                    }
                }
                .collect {}
        }
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
        if (state.value.isCreateEnabled) {
            // Navigate to create
        } else {
            // navigate to edit
        }
    }

    fun onSaveOrEditExternalReferral() {
        viewModelScope.launch {
            if (state.value.isSaveEnabled) {
                val referralCode = referralCodeTextFieldState.text.toString()
                val validation = validateReferralCode(referralCode)
                if (validation != null) {
                    state.update {
                        it.copy(
                            errorMessage = validation
                        )
                    }
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    val exists = thorChainApi.existsReferralCode(referralCode)
                    val existsMessage = if (exists) {
                        "Referral code successfully linked"
                    } else {
                        "Referral code does not exist"
                    }
                    state.update {
                        it.copy(
                            errorMessage = existsMessage
                        )
                    }
                }
            } else {
                // Got to edit screen
            }
        }
    }

    fun onPasteIconClick(content: String) {
        viewModelScope.launch {
            referralCodeTextFieldState.setTextAndPlaceCursorAtEnd(content)
            val validation = validateReferralCode(content)
            if (validation != null) {
                state.update {
                    it.copy(
                        errorMessage = validation
                    )
                }
            }
        }
    }

    private fun validateReferralCode(code: String): String? {
        if (code.isEmpty()) return "Referral code cannot be empty"
        if (code.length > 4) return "Referral code can be up to 4 characters"
        return null
    }
}