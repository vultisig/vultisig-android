package com.vultisig.wallet.ui.models.passcode

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.data.utils.safeLaunch
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
) {
    /**
     * True while the gate covers the app, including before the persisted state has been read.
     *
     * What is behind the cover is still composed and still in the semantics tree, so this decides
     * more than what is drawn: taps, the back button and accessibility focus all have to stop here
     * too, or the lock is only a lock to someone looking at the screen.
     */
    val isGateClosed: Boolean
        get() =
            when (passcodeState) {
                PasscodeState.Unknown,
                PasscodeState.Locked,
                PasscodeState.KeyUnavailable,
                PasscodeState.StoreUnavailable -> true
                PasscodeState.Unlocked,
                PasscodeState.Disabled -> false
            }
}

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
        //
        // safeLaunch, because that read decodes material from a keystore that can fail: an
        // exception escaping here would strand the state at Unknown, and the opaque cover the
        // guard draws while it waits would become the entire app until it is force-stopped.
        viewModelScope.safeLaunch { passcodeRepository.initialize() }

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
