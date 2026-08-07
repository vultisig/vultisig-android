package com.vultisig.wallet.ui.models.passcode

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
internal data class PasscodeGuardUiModel(
    val passcodeState: PasscodeState = PasscodeState.Unknown,
    /**
     * True once the passcode has been entered in this process.
     *
     * Turning the passcode off flips the state straight to [PasscodeState.Disabled], which composes
     * the device-credential gate for the first time this process with nothing yet authorised — so
     * the user is met by a system prompt in the same breath as proving their passcode. They have
     * already identified themselves; this remembers it.
     */
    val isIdentityProven: Boolean = false,
)

/** Exposes the lock state that decides whether the app content or the lock screen is shown. */
@HiltViewModel
internal class PasscodeGuardViewModel
@Inject
constructor(private val passcodeRepository: PasscodeRepository) : ViewModel() {

    private val _state = MutableStateFlow(PasscodeGuardUiModel())
    val state: StateFlow<PasscodeGuardUiModel> = _state.asStateFlow()

    init {
        // Reads the persisted credentials off the main thread; until it completes the state stays
        // Unknown and the guard shows nothing rather than flashing unlocked content.
        viewModelScope.launch { passcodeRepository.initialize() }

        viewModelScope.launch {
            passcodeRepository.state.collect { passcodeState -> _state.update(passcodeState) }
        }
    }

    private fun MutableStateFlow<PasscodeGuardUiModel>.update(passcodeState: PasscodeState) {
        value =
            value.copy(
                passcodeState = passcodeState,
                isIdentityProven = value.isIdentityProven || passcodeState == PasscodeState.Unlocked,
            )
    }
}
