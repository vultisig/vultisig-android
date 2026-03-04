package com.vultisig.wallet.ui.screens.backup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.ServerBackupResult
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.backup.RequestServerBackupUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
internal data class ServerBackupUiState(
    val vaultName: String = "",
    val isNameConfirmed: Boolean = false,
    val isEmailConfirmed: Boolean = false,
    val emailInnerState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
    val emailError: UiText = UiText.Empty,
    val errorBanner: UiText = UiText.Empty,
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
)

/**
 * ViewModel for the Server Backup screen. Manages email/password input,
 * validation, and the request to resend the vault backup share via email.
 *
 * Supports pre-filled email and vault name via navigation arguments
 * (e.g. when navigating from the post-keygen onboarding flow).
 */
@HiltViewModel
internal class ServerBackupViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val requestServerBackup: RequestServerBackupUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val state = MutableStateFlow(ServerBackupUiState())
    val emailFieldState = TextFieldState()
    val passwordFieldState = TextFieldState()

    private val args = savedStateHandle.toRoute<Route.ServerBackup>()
    private val vaultId = args.vaultId

    init {
        loadVaultInfo()
        collectEmailInput()
    }

    private fun loadVaultInfo() {
        viewModelScope.safeLaunch {
            val vault = vaultRepository.get(vaultId)
            val name = args.prefillName ?: vault?.name.orEmpty()
            val prefillEmail = args.prefillEmail

            state.update {
                it.copy(
                    vaultName = name,
                    isNameConfirmed = name.isNotEmpty(),
                    isEmailConfirmed = !prefillEmail.isNullOrEmpty(),
                )
            }

            if (!prefillEmail.isNullOrEmpty()) {
                emailFieldState.setTextAndPlaceCursorAtEnd(prefillEmail)
            }
        }
    }

    /**
     * Observes email text changes to perform inline validation and
     * clear any existing error banner when the user edits the email.
     */
    private fun collectEmailInput() {
        viewModelScope.launch {
            emailFieldState.textAsFlow().collect { typingEmail ->
                val emailStr = typingEmail.toString()
                val isValid = validateEmail(emailStr)
                val errorMessage =
                    UiText.StringResource(R.string.server_backup_email_error)
                        .takeIf { emailStr.isNotEmpty() && !isValid }
                        ?: UiText.Empty
                val innerState = when {
                    emailStr.isEmpty() -> VsTextInputFieldInnerState.Default
                    isValid -> VsTextInputFieldInnerState.Success
                    else -> VsTextInputFieldInnerState.Error
                }
                state.update {
                    it.copy(
                        emailInnerState = innerState,
                        emailError = errorMessage,
                        errorBanner = UiText.Empty,
                    )
                }
            }
        }
    }

    fun onEditEmail() {
        state.update { it.copy(isEmailConfirmed = false) }
    }

    fun clearEmailInput() {
        emailFieldState.clearText()
    }

    fun togglePasswordVisibility() {
        state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onSubmit() {
        if (state.value.isLoading) return

        val email = emailFieldState.text.toString()
        val password = passwordFieldState.text.toString()

        if (!validateEmail(email) || password.isEmpty()) return

        viewModelScope.safeLaunch(
            onError = {
                state.update {
                    it.copy(
                        isLoading = false,
                        errorBanner = UiText.StringResource(R.string.server_backup_error_bad_request),
                    )
                }
            }
        ) {
            state.update { it.copy(isLoading = true) }

            val result = requestServerBackup(
                vaultId = vaultId,
                email = email,
                password = password,
            )

            when (result) {
                is ServerBackupResult.Success ->
                    state.update { it.copy(isLoading = false, isSuccess = true) }

                is ServerBackupResult.Error -> {
                    val errorRes = when (result.type) {
                        ServerBackupResult.ErrorType.NETWORK_ERROR ->
                            R.string.server_backup_error_timeout

                        ServerBackupResult.ErrorType.TOO_MANY_REQUESTS ->
                            R.string.server_backup_error_too_many_requests

                        ServerBackupResult.ErrorType.INVALID_PASSWORD,
                        ServerBackupResult.ErrorType.BAD_REQUEST,
                        ServerBackupResult.ErrorType.UNKNOWN ->
                            R.string.server_backup_error_bad_request
                    }
                    state.update {
                        it.copy(
                            isLoading = false,
                            errorBanner = UiText.StringResource(errorRes),
                        )
                    }
                }
            }
        }
    }

    fun onSuccessClose() = back()

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    private fun validateEmail(email: String): Boolean =
        EMAIL_REGEX.matches(email)

    private companion object {
        val EMAIL_REGEX = Regex(
            "[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+"
        )
    }
}
