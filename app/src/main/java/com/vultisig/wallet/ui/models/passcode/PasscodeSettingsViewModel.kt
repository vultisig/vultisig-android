package com.vultisig.wallet.ui.models.passcode

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.AutoLockRepository
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.data.passcode.isConfigured
import com.vultisig.wallet.ui.components.BiometricUnlockLauncher
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Immutable
internal data class PasscodeSettingsUiModel(
    val isPasscodeEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.Default,
    /**
     * False until the persisted credentials have been read; the switch must not act before then.
     */
    val isReady: Boolean = false,
    /** Whether a biometric copy of the data key is stored — see `BiometricUnlockStore`. */
    val isBiometricUnlockEnabled: Boolean = false,
    /** What the last biometric attempt had to say for itself, if anything. */
    val biometricError: UiText? = null,
)

/** Backs Settings → Passcode encryption: the on/off switch, change, and auto-lock entry points. */
@HiltViewModel
internal class PasscodeSettingsViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val passcodeRepository: PasscodeRepository,
    autoLockRepository: AutoLockRepository,
) : ViewModel() {

    val state = MutableStateFlow(PasscodeSettingsUiModel())

    /**
     * Kept beside the repository flows rather than written straight into [state], which every
     * emission of the combine below rebuilds from scratch — a message written into that object
     * would be erased by the next unrelated one.
     */
    private val biometricError = MutableStateFlow<UiText?>(null)

    private var biometricJob: Job? = null

    init {
        viewModelScope.launch {
            passcodeRepository.initialize()
            combine(
                    passcodeRepository.state,
                    autoLockRepository.timeout,
                    passcodeRepository.isBiometricUnlockEnabled,
                    biometricError,
                ) { passcode, timeout, isBiometricEnabled, error ->
                    PasscodeSettingsUiModel(
                        isPasscodeEnabled = passcode.isConfigured,
                        autoLockTimeout = timeout,
                        isReady = passcode != PasscodeState.Unknown,
                        isBiometricUnlockEnabled = isBiometricEnabled,
                        biometricError = error,
                    )
                }
                .collect { state.value = it }
        }
    }

    /**
     * Turns the biometric shortcut on or off.
     *
     * Turning it on needs a match before the keystore will encrypt anything under the new key, so
     * both directions go through the same [launcher] seam the lock screen uses. Turning it off
     * needs no prompt: removing a copy is not reading it.
     */
    fun onBiometricUnlockChange(enabled: Boolean, launcher: BiometricUnlockLauncher) {
        // One at a time, so a second tap cannot start a second prompt over the same keystore key.
        if (biometricJob?.isActive == true) return
        biometricJob =
            viewModelScope.launch {
                biometricError.value = null
                if (!enabled) {
                    passcodeRepository.disableBiometricUnlock()
                    return@launch
                }

                val cipher = passcodeRepository.biometricEnableCipher()
                if (cipher == null) {
                    biometricError.value =
                        UiText.StringResource(R.string.passcode_biometric_enable_failed)
                    return@launch
                }

                // Null is a cancel or a dismissal — the user's own answer, and not a failure to
                // report back to them. The switch is driven by the repository flow, so it returns
                // to off on its own.
                val authorized = launcher.authenticate(cipher) ?: return@launch

                if (!passcodeRepository.enableBiometricUnlock(authorized)) {
                    biometricError.value =
                        UiText.StringResource(R.string.passcode_biometric_enable_failed)
                }
            }
    }

    /**
     * Both directions go through a prompt rather than acting on the switch alone: turning it on
     * needs a passcode to set, and turning it off needs proof the user knows the current one.
     */
    fun onPasscodeEnabledChange(enabled: Boolean) {
        // Until the persisted credentials have been read the switch reads as off even when a
        // passcode exists, and acting on that would route to Set and try to replace the wrap that
        // already-encrypted keyshares depend on.
        if (!state.value.isReady) return
        val action =
            if (enabled) Route.PasscodeEntryAction.Set else Route.PasscodeEntryAction.Disable
        viewModelScope.launch { navigator.route(Route.PasscodeEntry(action)) }
    }

    fun onChangePasscodeClick() {
        viewModelScope.launch {
            navigator.route(Route.PasscodeEntry(Route.PasscodeEntryAction.Change))
        }
    }

    fun onAutoLockClick() {
        viewModelScope.launch { navigator.route(Route.AutoLockSetting) }
    }
}
