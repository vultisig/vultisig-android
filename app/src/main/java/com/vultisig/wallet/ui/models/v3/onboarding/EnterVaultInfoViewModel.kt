package com.vultisig.wallet.ui.models.v3.onboarding

import android.util.Patterns
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GenerateUniqueName
import com.vultisig.wallet.data.usecases.IsVaultNameValid
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.screens.backup.PasswordState
import com.vultisig.wallet.ui.screens.backup.PasswordViewModelDelegate
import com.vultisig.wallet.ui.theme.v2.V2
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StepType(
    val title: UiText,
    val description: UiText,
    val logo: Int,
    val isPassword: Boolean,
    val descriptionHighlight: UiText? = null,
) {
    Name(
        title = UiText.StringResource(R.string.fast_vault_name_screen_title),
        description =UiText.StringResource(R.string.vault_setup_no_inspiration),
        logo = R.drawable.blue_feather,
        isPassword = false,
    ),
    Email(
        title = UiText.StringResource(R.string.email_enter_your_email),
        description =UiText.StringResource(R.string.email_only_used_once),
        logo = R.drawable.mail,
        isPassword = false,
    ),
    Password(
        title = UiText.StringResource(R.string.password_choose_a_password),
        description =UiText.StringResource(R.string.password_extra_layer),
        logo = R.drawable.center_lock,
        isPassword = true,
        descriptionHighlight = UiText.StringResource(R.string.password_extra_layer_highlight),
    )
}

enum class StepState(
    val backgroundColor: Color,
    val borderColor: Color,
    val borderWidth: Dp,
) {
    InProgress(
        backgroundColor = Color.Transparent,
        borderColor = V2.colors.neutrals.n50.copy(alpha = 0.2f),
        borderWidth = 3.dp,
    ),
    Done(
        backgroundColor = V2.colors.backgrounds.state.success.copy(
            alpha = 0.05f
        ),
        borderColor = Color.Transparent,
        borderWidth = 0.dp,
    ),

    Inactive(
        backgroundColor = Color.Transparent,
        borderColor = V2.colors.text.button.disabled,
        borderWidth = 0.dp,
    ),

}

@Immutable
internal data class EnterVaultInfoUiState(
    val activeStep: StepType = StepType.Name,
    val stepAndStates: Map<StepType, StepState> = emptyMap(),
    val inputTextFieldState: TextFieldState = TextFieldState(),
    val confirmPasswordTextFieldState: TextFieldState = TextFieldState(),
    val errorMessage: UiText? = null,
    val isNextButtonEnabled: Boolean = false,
    val innerState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
)

internal sealed interface EnterVaultInfoEvent {
    object Next : EnterVaultInfoEvent
    object Back : EnterVaultInfoEvent
    object Referral : EnterVaultInfoEvent
    object ClearInput : EnterVaultInfoEvent
    object TogglePasswordVisibility : EnterVaultInfoEvent
    object ToggleConfirmPasswordVisibility : EnterVaultInfoEvent
}


@HiltViewModel
internal class EnterVaultInfoViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,

    private val vaultRepository: VaultRepository,
    private val isNameLengthValid: IsVaultNameValid,
    private val generateUniqueName: GenerateUniqueName,

    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deviceCount: Int = savedStateHandle.toRoute<Route.EnterVaultInfo>().count
    val isSecureVault = deviceCount != 1

    private val passwordDelegate = PasswordViewModelDelegate()

    val uiState = MutableStateFlow(
        EnterVaultInfoUiState(
            confirmPasswordTextFieldState = passwordDelegate.confirmPasswordTextFieldState
        )
    )

    private var vaultNamesList = emptyList<String>()

    val nameTextFieldState: TextFieldState = TextFieldState()
    val emailTextFieldState: TextFieldState = TextFieldState()
    val passwordTextFieldState = passwordDelegate.passwordTextFieldState

    var nameInnerState = MutableStateFlow(VsTextInputFieldInnerState.Default)
    var emailInnerState = MutableStateFlow(VsTextInputFieldInnerState.Default)
    var passwordInnerState = MutableStateFlow(VsTextInputFieldInnerState.Default)

    var nameErrorMessage = MutableStateFlow<UiText?>(null)
    var emailErrorMessage = MutableStateFlow<UiText?>(null)
    var passwordErrorMessage = MutableStateFlow<UiText?>(null)

    init {
        initStepStates()
        collectStepStates()
        observeActiveStepState()
        generateVaultName()
        observeNameFieldChanges()
        observeEmailFieldChanges()
        observePasswordFieldChanges()
    }

    private fun initStepStates() {
        val stepAndStates = if (isSecureVault) {
            mapOf(StepType.Name to StepState.InProgress)
        } else {
            mapOf(
                StepType.Name to StepState.InProgress,
                StepType.Email to StepState.Inactive,
                StepType.Password to StepState.Inactive,
            )
        }
        uiState.update {
            it.copy(
                stepAndStates = stepAndStates
            )
        }
    }

    private fun generateVaultName() {
        viewModelScope.launch {
            vaultNamesList = vaultRepository.getAll().map { it.name }
            val proposeName = generateUniqueName("Secure Vault", vaultNamesList)
            nameTextFieldState.setTextAndPlaceCursorAtEnd(proposeName)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeActiveStepState() {
        uiState
            .map { it.activeStep }
            .distinctUntilChanged()
            .flatMapLatest { activeStep ->
                val (innerStateFlow, errorMessageFlow) = when (activeStep) {
                    StepType.Name -> nameInnerState to nameErrorMessage
                    StepType.Email -> emailInnerState to emailErrorMessage
                    StepType.Password -> passwordInnerState to passwordErrorMessage
                }
                combine(innerStateFlow, errorMessageFlow) { innerState, errorMessage ->
                    innerState to errorMessage
                }
            }
            .onEach { (innerState, errorMessage) ->
                uiState.update { it.copy(
                    innerState = innerState,
                    errorMessage = errorMessage)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun collectStepStates() {
        uiState
            .map { it.activeStep }
            .distinctUntilChanged()
            .combine(uiState.map { it.stepAndStates.keys }) { activeStep, steps ->
                val map = steps.associateWith { step ->
                    when {
                        step == activeStep -> StepState.InProgress
                        step.ordinal < activeStep.ordinal -> StepState.Done
                        else -> StepState.Inactive
                    }
                }

                val textFieldState = when (activeStep) {
                    StepType.Name -> nameTextFieldState
                    StepType.Email -> emailTextFieldState
                    StepType.Password -> passwordTextFieldState
                }

                uiState.update {
                    it.copy(
                        stepAndStates = map,
                        inputTextFieldState = textFieldState,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: EnterVaultInfoEvent) {
        when (event) {
            EnterVaultInfoEvent.Back -> prev()
            EnterVaultInfoEvent.Next -> next()
            EnterVaultInfoEvent.Referral -> referral()
            EnterVaultInfoEvent.ClearInput -> clearInput()
            EnterVaultInfoEvent.TogglePasswordVisibility -> togglePasswordVisibility()
            EnterVaultInfoEvent.ToggleConfirmPasswordVisibility -> toggleConfirmPasswordVisibility()
        }
    }

    private fun togglePasswordVisibility() {
        uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    private fun toggleConfirmPasswordVisibility() {
        uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    private fun clearInput() {
        when (uiState.value.activeStep) {
            StepType.Name -> nameTextFieldState.clearText()
            StepType.Email -> emailTextFieldState.clearText()
            StepType.Password -> passwordTextFieldState.clearText()
        }
    }

    private fun prev() {
        viewModelScope.launch {
            val currentStep = uiState.value.activeStep
            val currentStepIndex = uiState.value.stepAndStates.keys.indexOf(currentStep)
            val nextIndex = currentStepIndex - 1
            if (nextIndex < 0) {
                back()
                return@launch
            }
            val newStep = uiState.value.stepAndStates.keys.elementAt(nextIndex)
            uiState.update {
                it.copy(activeStep = newStep)
            }
        }
    }

    private fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }

    private fun next() {
        viewModelScope.launch {
            val currentStep = uiState.value.activeStep
            val canProceedToNextStep = validateAllInputs()
            if (canProceedToNextStep.not())
                return@launch

            val currentStepIndex = uiState.value.stepAndStates.keys.indexOf(currentStep)
            val nextIndex = currentStepIndex + 1
            if (nextIndex >= uiState.value.stepAndStates.size) {
                navigateToNextScreen()
                return@launch
            }
            val newStep = uiState.value.stepAndStates.keys.elementAt(nextIndex)
            uiState.update {
                it.copy(activeStep = newStep)
            }
        }
    }

    private fun validateAllInputs(): Boolean {
        val currentStep = uiState.value.activeStep
        val canProceedToNextStep = when (currentStep) {
            StepType.Name -> {
                isNameValid()
            }
            StepType.Email -> {
                val typingEmail = emailTextFieldState.text.toString()
                val validateEmail = validateEmail(typingEmail)
                emailInnerState.value = getEmailInnerState(typingEmail, validateEmail )
                validateEmail
            }
            StepType.Password -> {
                val password = passwordTextFieldState.text.toString()
                val confirmPassword = passwordDelegate.confirmPasswordTextFieldState.text.toString()
                when {
                    password.isEmpty() || confirmPassword.isEmpty() -> false
                    password == confirmPassword -> true
                    else -> {
                        passwordInnerState.value = VsTextInputFieldInnerState.Error
                        passwordErrorMessage.value =
                            StringResource(R.string.fast_vault_password_screen_error)
                        false
                    }
                }
            }
        }
        return canProceedToNextStep
    }


    private fun observeNameFieldChanges() = viewModelScope.launch {
        nameTextFieldState.textAsFlow().collectLatest {
            if (it.isNotEmpty()) {
                validateNameInput()
            } else {
                nameErrorMessage.value = null
                uiState.update { currentState ->
                    currentState.copy(
                        isNextButtonEnabled = false
                    )
                }
            }
        }
    }

    private fun validateNameInput() = viewModelScope.launch {
        val name = nameTextFieldState.text.toString().trim()

        val errorMessage = when {
            !isNameValid(name) -> StringResource(R.string.naming_vault_screen_invalid_name)
            !isNameAvailable(name) -> StringResource(R.string.name_vault_vault_with_this_name_already_exists)
            else -> null
        }

        val isNextButtonEnabled = errorMessage == null
        nameErrorMessage.value = errorMessage
        uiState.update {
            it.copy(
                isNextButtonEnabled = isNextButtonEnabled,
            )
        }
    }

    private fun isNameValid(name: String): Boolean {
        return isNameLengthValid(name)
    }

    private fun isNameAvailable(name: String): Boolean =
        vaultNamesList.none { it == name }



    private fun observeEmailFieldChanges() {
        viewModelScope.launch {
            emailTextFieldState.textAsFlow().collect { email ->
                val isEmailValid = validateEmail(email)
                val errorMessage =
                    StringResource(R.string.keygen_email_error)
                        .takeIf { email.isNotEmpty() && !isEmailValid } ?: UiText.Empty
                val innerState = getEmailInnerState(
                    email = email.toString(),
                    isEmailValid = isEmailValid
                )

                emailInnerState.value = innerState
                emailErrorMessage.value = errorMessage
            }
        }
    }

    private fun observePasswordFieldChanges() {
        viewModelScope.launch {
            passwordDelegate.validatePasswords().collect { passwordState ->
                val errorMessage = if (passwordState is PasswordState.Mismatch) {
                    StringResource(R.string.fast_vault_password_screen_error)
                } else {
                    null
                }
                passwordInnerState.value = if (errorMessage != null)
                    VsTextInputFieldInnerState.Error
                else
                    VsTextInputFieldInnerState.Default
                passwordErrorMessage.value = errorMessage
                if (uiState.value.activeStep == StepType.Password) {
                    uiState.update {
                        it.copy(isNextButtonEnabled = passwordState is PasswordState.Valid)
                    }
                }
            }
        }
    }

    private fun validateEmail(typingEmail: CharSequence) =
        Patterns.EMAIL_ADDRESS.matcher(typingEmail).matches()

    private fun getEmailInnerState(
        email: String,
        isEmailValid: Boolean,
    ) = if (email.isEmpty())
        VsTextInputFieldInnerState.Default
    else {
        if (isEmailValid)
            VsTextInputFieldInnerState.Success
        else VsTextInputFieldInnerState.Error
    }

    private fun navigateToNextScreen() {
        val name = nameTextFieldState.text.toString()
        val email = emailTextFieldState.text.toString()
        val password = passwordTextFieldState.text.toString()
        viewModelScope.launch {
            if (isSecureVault) {
                navigator.route(
                    Route.Keygen.PeerDiscovery(
                        action = TssAction.KEYGEN,
                        vaultName = name,
                    )
                )
            } else {
                navigator.route(
                    Route.Keygen.PeerDiscovery(
                        action = TssAction.KEYGEN,
                        vaultName = name,
                        email = email,
                        password = password,
                        hint = null,
                        vaultId = null,
                        deviceCount = deviceCount,
                    )
                )
            }
        }
    }

    private fun isNameValid(): Boolean {
        val name = nameTextFieldState.text.toString()
        return isNameValid(name) && isNameAvailable(name)
    }

    private fun referral() {

    }


}