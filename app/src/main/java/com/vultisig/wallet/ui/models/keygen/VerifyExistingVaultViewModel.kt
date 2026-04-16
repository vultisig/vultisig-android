package com.vultisig.wallet.ui.models.keygen

import android.util.Patterns
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.PasswordCheckResult
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.keygen.VerifyExistingVaultStepState
import com.vultisig.wallet.ui.screens.keygen.VerifyExistingVaultStepType
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@Immutable
internal data class VerifyExistingVaultUiState(
    val activeStep: VerifyExistingVaultStepType = VerifyExistingVaultStepType.Email,
    val stepAndStates: Map<VerifyExistingVaultStepType, VerifyExistingVaultStepState> = emptyMap(),
    val textFieldHint: UiText = UiText.Empty,
    val errorMessage: UiText? = null,
    val isNextButtonEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val innerState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
    val isPasswordVisible: Boolean = false,
)

internal sealed interface VerifyExistingVaultEvent {
    data object Next : VerifyExistingVaultEvent

    data object Back : VerifyExistingVaultEvent

    data object ClearInput : VerifyExistingVaultEvent

    data object TogglePasswordVisibility : VerifyExistingVaultEvent
}

@HiltViewModel
internal class VerifyExistingVaultViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val vultiSignerRepository: VultiSignerRepository,
    private val vaultRepository: VaultRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.VerifyExistingVault>()
    private val vaultName = args.name
    private val tssAction = args.tssAction
    private val vaultId = args.vaultId

    private val _uiState = MutableStateFlow(VerifyExistingVaultUiState())
    val uiState: StateFlow<VerifyExistingVaultUiState> = _uiState.asStateFlow()

    val emailTextFieldState = TextFieldState()
    val passwordTextFieldState = TextFieldState()

    private var emailInnerState = MutableStateFlow(VsTextInputFieldInnerState.Default)
    private var passwordInnerState = MutableStateFlow(VsTextInputFieldInnerState.Default)

    private var emailErrorMessage = MutableStateFlow<UiText?>(null)
    private var passwordErrorMessage = MutableStateFlow<UiText?>(null)

    init {
        initStepStates()
        collectStepStates()
        observeActiveStepState()
        observeEmailFieldChanges()
        observePasswordFieldChanges()
    }

    private fun initStepStates() {
        val stepAndStates =
            mapOf(
                VerifyExistingVaultStepType.Email to VerifyExistingVaultStepState.InProgress,
                VerifyExistingVaultStepType.Password to VerifyExistingVaultStepState.Inactive,
            )
        _uiState.update { it.copy(stepAndStates = stepAndStates) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeActiveStepState() {
        uiState
            .map { it.activeStep }
            .distinctUntilChanged()
            .flatMapLatest { activeStep ->
                val (innerStateFlow, errorMessageFlow) =
                    when (activeStep) {
                        VerifyExistingVaultStepType.Email -> emailInnerState to emailErrorMessage
                        VerifyExistingVaultStepType.Password ->
                            passwordInnerState to passwordErrorMessage
                    }
                combine(innerStateFlow, errorMessageFlow) { innerState, errorMessage ->
                    innerState to errorMessage
                }
            }
            .onEach { (innerState, errorMessage) ->
                _uiState.update { it.copy(innerState = innerState, errorMessage = errorMessage) }
            }
            .launchIn(viewModelScope)
    }

    private fun collectStepStates() {
        combine(
                uiState.map { it.activeStep }.distinctUntilChanged(),
                uiState.map { it.stepAndStates.keys },
                emailTextFieldState.textAsFlow().map { it.toString() },
                passwordTextFieldState.textAsFlow().map { it.toString() },
            ) { activeStep, steps, email, password ->
                val map =
                    steps.associateWith { step ->
                        when {
                            step == activeStep -> VerifyExistingVaultStepState.InProgress
                            step.ordinal < activeStep.ordinal -> VerifyExistingVaultStepState.Done
                            else -> VerifyExistingVaultStepState.Inactive
                        }
                    }

                val hint =
                    when (activeStep) {
                        VerifyExistingVaultStepType.Email ->
                            StringResource(R.string.enter_email_screen_title)
                        VerifyExistingVaultStepType.Password ->
                            StringResource(R.string.keysign_password_enter_your_password)
                    }

                val isNextButtonEnabled =
                    when (activeStep) {
                        VerifyExistingVaultStepType.Email -> validateEmail(email)
                        VerifyExistingVaultStepType.Password -> password.isNotBlank()
                    }

                _uiState.update {
                    it.copy(
                        stepAndStates = map,
                        textFieldHint = hint,
                        isNextButtonEnabled = isNextButtonEnabled,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: VerifyExistingVaultEvent) {
        when (event) {
            VerifyExistingVaultEvent.Back -> prev()
            VerifyExistingVaultEvent.Next -> next()
            VerifyExistingVaultEvent.ClearInput -> clearInput()
            VerifyExistingVaultEvent.TogglePasswordVisibility -> togglePasswordVisibility()
        }
    }

    private fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    private fun clearInput() {
        when (uiState.value.activeStep) {
            VerifyExistingVaultStepType.Email -> emailTextFieldState.clearText()
            VerifyExistingVaultStepType.Password -> passwordTextFieldState.clearText()
        }
    }

    private fun prev() {
        viewModelScope.launch {
            if (uiState.value.isLoading) return@launch
            val currentStep = uiState.value.activeStep
            val currentStepIndex = uiState.value.stepAndStates.keys.indexOf(currentStep)
            val nextIndex = currentStepIndex - 1
            if (nextIndex < 0) {
                navigator.navigate(Destination.Back)
                return@launch
            }
            val newStep = uiState.value.stepAndStates.keys.elementAt(nextIndex)
            _uiState.update { it.copy(activeStep = newStep) }
        }
    }

    private fun next() {
        viewModelScope.launch {
            if (uiState.value.isLoading) return@launch
            val currentStep = uiState.value.activeStep
            val isValid = validateCurrentStep()
            if (!isValid) return@launch

            val currentStepIndex = uiState.value.stepAndStates.keys.indexOf(currentStep)
            val nextIndex = currentStepIndex + 1
            if (nextIndex >= uiState.value.stepAndStates.size) {
                verifyPasswordAndNavigate()
                return@launch
            }
            val newStep = uiState.value.stepAndStates.keys.elementAt(nextIndex)
            _uiState.update { it.copy(activeStep = newStep) }
        }
    }

    private fun validateCurrentStep(): Boolean {
        return when (uiState.value.activeStep) {
            VerifyExistingVaultStepType.Email -> {
                val email = emailTextFieldState.text.toString()
                val isValid = validateEmail(email)
                emailInnerState.value = getEmailInnerState(email, isValid)
                isValid
            }
            VerifyExistingVaultStepType.Password -> {
                val password = passwordTextFieldState.text.toString()
                password.isNotBlank()
            }
        }
    }

    private fun verifyPasswordAndNavigate() {
        val password = passwordTextFieldState.text.toString()
        if (password.isBlank()) return

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to verify vault password")
                passwordInnerState.value = VsTextInputFieldInnerState.Error
                passwordErrorMessage.value = StringResource(R.string.dialog_default_error_body)
                _uiState.update { it.copy(isLoading = false) }
            }
        ) {
            val vault = vaultRepository.get(vaultId)
            if (vault == null) {
                passwordInnerState.value = VsTextInputFieldInnerState.Error
                passwordErrorMessage.value =
                    StringResource(R.string.push_notification_vault_not_found)
                _uiState.update { it.copy(isLoading = false) }
                return@safeLaunch
            }

            when (val result = vultiSignerRepository.checkPassword(vault.pubKeyECDSA, password)) {
                is PasswordCheckResult.Valid -> {
                    navigator.route(
                        Route.Keygen.PeerDiscovery(
                            vaultName = vaultName,
                            email = emailTextFieldState.text.toString(),
                            action = tssAction,
                            vaultId = vaultId,
                            password = password,
                        )
                    )
                }
                is PasswordCheckResult.Invalid -> {
                    passwordInnerState.value = VsTextInputFieldInnerState.Error
                    passwordErrorMessage.value =
                        StringResource(R.string.fast_vault_invalid_password)
                }
                is PasswordCheckResult.NetworkError -> {
                    passwordInnerState.value = VsTextInputFieldInnerState.Error
                    passwordErrorMessage.value = StringResource(R.string.network_connection_lost)
                }
                is PasswordCheckResult.Error -> {
                    passwordInnerState.value = VsTextInputFieldInnerState.Error
                    passwordErrorMessage.value =
                        UiText.StringResource(R.string.dialog_default_error_body)
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun observeEmailFieldChanges() {
        viewModelScope.launch {
            emailTextFieldState.textAsFlow().collectLatest { email ->
                val isEmailValid = validateEmail(email)
                val errorMessage =
                    StringResource(R.string.keygen_email_error).takeIf {
                        email.isNotEmpty() && !isEmailValid
                    }
                val innerState = getEmailInnerState(email.toString(), isEmailValid)

                emailInnerState.value = innerState
                emailErrorMessage.value = errorMessage
            }
        }
    }

    private fun observePasswordFieldChanges() {
        viewModelScope.launch {
            passwordTextFieldState.textAsFlow().collectLatest { _ ->
                if (uiState.value.activeStep == VerifyExistingVaultStepType.Password) {
                    passwordInnerState.value = VsTextInputFieldInnerState.Default
                    passwordErrorMessage.value = null
                }
            }
        }
    }

    private fun validateEmail(typingEmail: CharSequence) =
        Patterns.EMAIL_ADDRESS.matcher(typingEmail).matches()

    private fun getEmailInnerState(email: String, isEmailValid: Boolean) =
        if (email.isEmpty()) VsTextInputFieldInnerState.Default
        else {
            if (isEmailValid) VsTextInputFieldInnerState.Success
            else VsTextInputFieldInnerState.Error
        }
}
