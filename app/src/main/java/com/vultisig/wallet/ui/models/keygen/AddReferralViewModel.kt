package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepositoryContract
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AddReferralUiModel(
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    val innerState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
)

@HiltViewModel
internal class AddReferralViewModel
@Inject
constructor(
    private val thorChainApi: ThorChainApi,
    private val referralCodeSettingsRepository: ReferralCodeSettingsRepositoryContract,
) : ViewModel() {

    val state = MutableStateFlow(AddReferralUiModel())
    val textFieldState = TextFieldState(referralCodeSettingsRepository.getPendingReferral() ?: "")
    private var applyReferralJob: Job? = null

    init {
        viewModelScope.launch {
            textFieldState.textAsFlow().collect { text ->
                if (state.value.isLoading) return@collect

                validateLocalInput(text.toString().trim())
            }
        }
    }

    fun applyReferral(onSuccess: (String) -> Unit) {
        if (state.value.isLoading) return

        val code = textFieldState.text.toString().trim()
        val normalizedCode = code.uppercase()

        if (code.length > MAX_REFERRAL_LENGTH) {
            state.update {
                it.copy(
                    errorMessage = UiText.StringResource(R.string.add_referral_error_length),
                    innerState = VsTextInputFieldInnerState.Error,
                )
            }
            return
        }

        if (!code.matches(ALPHANUMERIC_REGEX)) {
            state.update {
                it.copy(
                    errorMessage = UiText.StringResource(R.string.add_referral_error_chars),
                    innerState = VsTextInputFieldInnerState.Error,
                )
            }
            return
        }

        state.update { it.copy(isLoading = true, errorMessage = null) }

        applyReferralJob =
            viewModelScope.launch {
                try {
                    val exists =
                        withContext(Dispatchers.IO) {
                            thorChainApi.existsReferralCode(normalizedCode)
                        }

                    if (exists) {
                        referralCodeSettingsRepository.setPendingReferral(normalizedCode)
                        state.update { it.copy(isLoading = false) }
                        onSuccess(normalizedCode)
                    } else {
                        state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage =
                                    UiText.StringResource(R.string.add_referral_error_not_found),
                                innerState = VsTextInputFieldInnerState.Error,
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    state.update { it.copy(isLoading = false) }
                    throw e
                } catch (_: Exception) {
                    state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage =
                                UiText.StringResource(R.string.add_referral_error_network),
                            innerState = VsTextInputFieldInnerState.Error,
                        )
                    }
                } finally {
                    applyReferralJob = null
                }
            }
    }

    fun cancelApplyReferral() {
        applyReferralJob?.cancel()
        applyReferralJob = null
        resetToPendingReferral()
    }

    fun resetToPendingReferral() {
        textFieldState.setTextAndPlaceCursorAtEnd(
            referralCodeSettingsRepository.getPendingReferral().orEmpty()
        )
        state.update {
            it.copy(
                isLoading = false,
                errorMessage = null,
                innerState = VsTextInputFieldInnerState.Default,
            )
        }
    }

    fun clearInput() {
        textFieldState.edit { replace(0, length, "") }
    }

    private fun validateLocalInput(code: String) {
        val validationError =
            when {
                code.isEmpty() -> null
                code.length > MAX_REFERRAL_LENGTH ->
                    UiText.StringResource(R.string.add_referral_error_length)
                !code.matches(ALPHANUMERIC_REGEX) ->
                    UiText.StringResource(R.string.add_referral_error_chars)
                else -> null
            }

        state.update {
            it.copy(
                errorMessage = validationError,
                innerState =
                    if (validationError != null) VsTextInputFieldInnerState.Error
                    else VsTextInputFieldInnerState.Default,
            )
        }
    }

    private companion object {
        const val MAX_REFERRAL_LENGTH = 4
        val ALPHANUMERIC_REGEX = Regex("^[A-Za-z0-9]+$")
    }
}
