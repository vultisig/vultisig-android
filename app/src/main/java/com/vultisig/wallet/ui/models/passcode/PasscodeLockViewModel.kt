package com.vultisig.wallet.ui.models.passcode

import android.os.SystemClock
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.passcode.PASSCODE_LENGTH
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.data.passcode.PasscodeUnlockResult
import com.vultisig.wallet.ui.components.BiometricUnlockLauncher
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the last passcode entry was rejected, if it was. */
@Immutable
internal sealed interface PasscodeLockError {
    /** The passcode was wrong; [remainingAttempts] tries remain before entry is throttled. */
    data class Wrong(val remainingAttempts: Int) : PasscodeLockError

    /** Entry is throttled for another [remainingSeconds]. */
    data class LockedOut(val remainingSeconds: Long) : PasscodeLockError

    /** The field held something other than digits — reachable with a hardware keyboard. */
    data object NotDigits : PasscodeLockError

    /**
     * The biometric shortcut was offered and is not there any more — most often because enrolling a
     * new face or finger destroyed the key it was held under.
     *
     * A failed or cancelled match is not this: the user watched that happen and the passcode field
     * is already in front of them, so saying "biometrics failed" would add nothing.
     */
    data object BiometricUnavailable : PasscodeLockError
}

@Immutable
internal data class PasscodeLockUiModel(
    val isVerifying: Boolean = false,
    val error: PasscodeLockError? = null,
    /** Whether this device holds a biometric copy of the data key to offer as a shortcut. */
    val isBiometricUnlockEnabled: Boolean = false,
) {
    val isInputEnabled: Boolean
        get() = !isVerifying && error !is PasscodeLockError.LockedOut
}

/**
 * Drives the App Locked screen: collects the passcode digits, verifies them, and counts down the
 * throttle when too many were wrong.
 */
@HiltViewModel
internal class PasscodeLockViewModel(
    private val passcodeRepository: PasscodeRepository,
    private val elapsedRealtimeMillis: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        passcodeRepository: PasscodeRepository
    ) : this(passcodeRepository, SystemClock::elapsedRealtime)

    val textFieldState = TextFieldState()

    val state = MutableStateFlow(PasscodeLockUiModel())

    private var verifyJob: Job? = null
    private var countdownJob: Job? = null
    private var biometricJob: Job? = null

    init {
        viewModelScope.launch {
            passcodeRepository.isBiometricUnlockEnabled.collect { enabled ->
                state.update { it.copy(isBiometricUnlockEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            textFieldState.textAsFlow().collect { text ->
                // Clear a stale error as soon as the user starts over, so the screen does not keep
                // accusing them while they type the next attempt. Only a non-empty field counts as
                // starting over: emptying it is how this view model reacts to a wrong passcode, and
                // treating that as a fresh start would wipe the error in the frame it appears.
                if (text.isNotEmpty() && state.value.error !is PasscodeLockError.LockedOut) {
                    state.update { it.copy(error = null) }
                }
                // Length alone is not enough to call this a passcode: KeyboardType is only an IME
                // hint, so a hardware keyboard can put letters in the field, and passing those on
                // trips requireValidPasscode — whose exception escapes this collector and takes
                // the process with it.
                if (text.length == PASSCODE_LENGTH) {
                    val entered = text.toString()
                    if (entered.all(Char::isDigit)) {
                        verify(entered)
                    } else {
                        textFieldState.clearText()
                        state.update { it.copy(error = PasscodeLockError.NotDigits) }
                    }
                }
            }
        }

        // This view model outlives any single lock, because the guard hosts it outside the nav
        // graph and it is therefore scoped to the activity. Without this, a half-typed or rejected
        // attempt is still sitting in the field the next time the app locks, so the user returns to
        // pre-filled cells and a stale error rather than a clean prompt.
        viewModelScope.launch {
            passcodeRepository.state.collect { passcodeState ->
                if (passcodeState == PasscodeState.Locked) {
                    reset()
                }
            }
        }
    }

    /**
     * Returns the prompt to its blank state, abandoning anything left over from a previous lock.
     */
    private fun reset() {
        verifyJob?.cancel()
        countdownJob?.cancel()
        biometricJob?.cancel()
        textFieldState.clearText()
        // Everything the previous lock left behind goes, except whether there is a shortcut to
        // offer: that is a property of the device, not of the attempt, and re-reading it here
        // would blank the link for as long as the collector takes to publish it again.
        state.value =
            PasscodeLockUiModel(isBiometricUnlockEnabled = state.value.isBiometricUnlockEnabled)
    }

    /**
     * Runs the biometric shortcut, on a tap and never on its own.
     *
     * A passcode user chose the passcode; a prompt that fires by itself unlocks the app for whoever
     * is holding the phone, and does it before they can decline.
     */
    fun onUseBiometricsClick(launcher: BiometricUnlockLauncher) {
        // One at a time. A second tap while the system sheet is up would start a second
        // authentication against the same keystore key.
        if (biometricJob?.isActive == true) return
        biometricJob =
            viewModelScope.launch {
                state.update { it.copy(isVerifying = true, error = null) }
                try {
                    val cipher = passcodeRepository.biometricUnlockCipher()
                    if (cipher == null) {
                        // The copy has just been dropped — an enrolment change is the usual
                        // reason. The link goes with it, so the screen has to say why.
                        state.update { it.copy(error = PasscodeLockError.BiometricUnavailable) }
                        return@launch
                    }

                    // Null is a cancel, a dismissal or a lockout. The user caused it and can see
                    // it; the passcode field is already in front of them.
                    val authorized = launcher.authenticate(cipher) ?: return@launch

                    when (passcodeRepository.unlockWithBiometrics(authorized)) {
                        // The repository's state flips to Unlocked and the guard takes the screen
                        // down; there is nothing left to do here.
                        is PasscodeUnlockResult.Success -> Unit
                        // The match succeeded and the key still did not come back, which is the
                        // one biometric failure worth reporting: from the user's side the
                        // hardware said yes and nothing happened.
                        else ->
                            state.update { it.copy(error = PasscodeLockError.BiometricUnavailable) }
                    }
                } finally {
                    state.update { it.copy(isVerifying = false) }
                }
            }
    }

    private fun verify(passcode: String) {
        // Restarting rather than ignoring: the field cannot reach full length again until the
        // in-flight attempt has cleared it, so a queued second attempt would be the same passcode.
        verifyJob?.cancel()
        verifyJob =
            viewModelScope.launch {
                state.update { it.copy(isVerifying = true) }
                val result = passcodeRepository.unlock(passcode)
                state.update { it.copy(isVerifying = false) }
                when (result) {
                    // The repository's state flow flips to Unlocked and the guard swaps the screen
                    // out; there is nothing left for this view model to do.
                    is PasscodeUnlockResult.Success -> Unit
                    is PasscodeUnlockResult.Wrong -> {
                        textFieldState.clearText()
                        state.update {
                            it.copy(error = PasscodeLockError.Wrong(result.remainingAttempts))
                        }
                    }
                    is PasscodeUnlockResult.LockedOut -> {
                        textFieldState.clearText()
                        startCountdown(result.retryAfterMillis)
                    }
                    // Nothing on the lock screen can fail this way — it only ever unlocks — but
                    // the branch keeps the result exhaustive rather than silently swallowed.
                    is PasscodeUnlockResult.Failed -> textFieldState.clearText()
                }
            }
    }

    private fun startCountdown(retryAfterMillis: Long) {
        countdownJob?.cancel()
        countdownJob =
            viewModelScope.launch {
                countdownSeconds(retryAfterMillis, elapsedRealtimeMillis) { remainingSeconds ->
                    state.update { it.copy(error = PasscodeLockError.LockedOut(remainingSeconds)) }
                }
                state.update { it.copy(error = null) }
            }
    }
}
