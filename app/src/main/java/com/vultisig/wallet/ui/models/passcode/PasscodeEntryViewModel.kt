package com.vultisig.wallet.ui.models.passcode

import android.os.SystemClock
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.passcode.PASSCODE_LENGTH
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeUnlockResult
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.PasscodeEntryAction
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which passcode the prompt is currently asking for. */
internal enum class PasscodeEntryStep {
    /** The passcode already in place, proving the user may change this setting. */
    Current,
    /** The passcode being chosen. */
    New,
    /** The chosen passcode again, to catch a typo before it locks anyone out. */
    Confirm,
}

@Immutable
internal sealed interface PasscodeEntryError {
    data class Wrong(val remainingAttempts: Int) : PasscodeEntryError

    data class LockedOut(val remainingSeconds: Long) : PasscodeEntryError

    data object Mismatch : PasscodeEntryError

    /** The passcode was right but the operation could not be carried out; nothing changed. */
    data object OperationFailed : PasscodeEntryError

    /** The field held something other than digits — reachable with a hardware keyboard. */
    data object NotDigits : PasscodeEntryError
}

@Immutable
internal data class PasscodeEntryUiModel(
    val action: PasscodeEntryAction = PasscodeEntryAction.Set,
    val step: PasscodeEntryStep = PasscodeEntryStep.New,
    val error: PasscodeEntryError? = null,
    val isBusy: Boolean = false,
) {
    val isInputEnabled: Boolean
        get() = !isBusy && error !is PasscodeEntryError.LockedOut
}

/** Drives the set / change / disable passcode prompts. */
@HiltViewModel
internal class PasscodeEntryViewModel(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val passcodeRepository: PasscodeRepository,
    private val elapsedRealtimeMillis: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        navigator: Navigator<Destination>,
        passcodeRepository: PasscodeRepository,
    ) : this(savedStateHandle, navigator, passcodeRepository, SystemClock::elapsedRealtime)

    private val action = savedStateHandle.toRoute<Route.PasscodeEntry>().action

    val textFieldState = TextFieldState()

    val state =
        MutableStateFlow(
            PasscodeEntryUiModel(
                action = action,
                step =
                    when (action) {
                        PasscodeEntryAction.Set -> PasscodeEntryStep.New
                        PasscodeEntryAction.Change,
                        PasscodeEntryAction.Disable -> PasscodeEntryStep.Current
                    },
            )
        )

    private var currentPasscode: String? = null
    private var newPasscode: String? = null

    private var submitJob: Job? = null
    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            textFieldState.textAsFlow().collect { text ->
                // Only a non-empty field counts as the user starting over. Every failure path here
                // clears the field, and that clear is itself an emission — treating it as a fresh
                // start would wipe the error in the same frame it was set, so nothing is shown.
                if (text.isNotEmpty() && state.value.error !is PasscodeEntryError.LockedOut) {
                    state.update { it.copy(error = null) }
                }
                // Length alone is not enough to call this a passcode. KeyboardType is only an
                // IME hint, so a hardware keyboard can put letters in the field; passing those on
                // would trip requireValidPasscode, whose exception escapes this collector and
                // takes the process with it.
                if (text.length == PASSCODE_LENGTH) {
                    val entered = text.toString()
                    if (entered.all(Char::isDigit)) {
                        submit(entered)
                    } else {
                        fail(PasscodeEntryError.NotDigits)
                    }
                }
            }
        }
    }

    private fun submit(passcode: String) {
        submitJob?.cancel()
        submitJob =
            viewModelScope.launch {
                when (state.value.step) {
                    PasscodeEntryStep.Current -> onCurrentEntered(passcode)
                    PasscodeEntryStep.New -> onNewEntered(passcode)
                    PasscodeEntryStep.Confirm -> onConfirmEntered(passcode)
                }
            }
    }

    private suspend fun onCurrentEntered(passcode: String) {
        // unlock() is the verification: the app is already unlocked when this screen is reachable,
        // so on success it is a no-op beyond clearing the failed-attempt counter, and on failure it
        // applies the same throttle as the lock screen.
        val result = withBusyState { passcodeRepository.unlock(passcode) }
        when (result) {
            is PasscodeUnlockResult.Success -> {
                currentPasscode = passcode
                textFieldState.clearText()
                when (action) {
                    PasscodeEntryAction.Disable -> disable(passcode)
                    else -> state.update { it.copy(step = PasscodeEntryStep.New) }
                }
            }
            is PasscodeUnlockResult.Wrong ->
                fail(PasscodeEntryError.Wrong(result.remainingAttempts))
            is PasscodeUnlockResult.LockedOut -> startCountdown(result.retryAfterMillis)
            is PasscodeUnlockResult.Failed -> fail(PasscodeEntryError.OperationFailed)
        }
    }

    private fun onNewEntered(passcode: String) {
        newPasscode = passcode
        textFieldState.clearText()
        state.update { it.copy(step = PasscodeEntryStep.Confirm) }
    }

    private suspend fun onConfirmEntered(passcode: String) {
        if (passcode != newPasscode) {
            // Back to choosing rather than re-confirming: the user does not know which of the two
            // entries was the typo, so asking them to repeat the same unknown value is a dead end.
            newPasscode = null
            state.update { it.copy(step = PasscodeEntryStep.New) }
            fail(PasscodeEntryError.Mismatch)
            return
        }

        when (action) {
            PasscodeEntryAction.Set -> {
                when (withBusyState { passcodeRepository.setPasscode(passcode) }) {
                    is PasscodeUnlockResult.Success -> close()
                    // Nothing was changed, so send the user back to choosing rather than leaving
                    // them on a Confirm step for a passcode that was never stored.
                    else -> {
                        newPasscode = null
                        state.update { it.copy(step = PasscodeEntryStep.New) }
                        fail(PasscodeEntryError.OperationFailed)
                    }
                }
            }
            PasscodeEntryAction.Change -> {
                // Reachable only if the verified passcode was dropped underneath us — process death
                // restoring this screen mid-flow. Returning silently would strand the user on a
                // Confirm step that can never complete, so send them back to prove themselves.
                val current = currentPasscode ?: return restartFromCurrentStep()
                when (
                    val result = withBusyState {
                        passcodeRepository.changePasscode(current, passcode)
                    }
                ) {
                    is PasscodeUnlockResult.Success -> close()
                    is PasscodeUnlockResult.Wrong ->
                        fail(PasscodeEntryError.Wrong(result.remainingAttempts))
                    is PasscodeUnlockResult.LockedOut -> startCountdown(result.retryAfterMillis)
                    is PasscodeUnlockResult.Failed -> fail(PasscodeEntryError.OperationFailed)
                }
            }
            // Disable never reaches Confirm; it completes as soon as the current passcode checks
            // out, because there is nothing to choose.
            PasscodeEntryAction.Disable -> close()
        }
    }

    private suspend fun disable(passcode: String) {
        when (val result = withBusyState { passcodeRepository.disablePasscode(passcode) }) {
            is PasscodeUnlockResult.Success -> close()
            is PasscodeUnlockResult.Wrong ->
                fail(PasscodeEntryError.Wrong(result.remainingAttempts))
            is PasscodeUnlockResult.LockedOut -> startCountdown(result.retryAfterMillis)
            // A keyshare would not decrypt, so the passcode is still in place. Reported rather
            // than thrown: the alternative crashed the app and left it impossible to turn off.
            is PasscodeUnlockResult.Failed -> {
                state.update { it.copy(step = PasscodeEntryStep.Current) }
                fail(PasscodeEntryError.OperationFailed)
            }
        }
    }

    private suspend fun <T> withBusyState(block: suspend () -> T): T {
        state.update { it.copy(isBusy = true) }
        try {
            return block()
        } finally {
            state.update { it.copy(isBusy = false) }
        }
    }

    private fun fail(error: PasscodeEntryError) {
        textFieldState.clearText()
        state.update { it.copy(error = error) }
    }

    private fun startCountdown(retryAfterMillis: Long) {
        textFieldState.clearText()
        countdownJob?.cancel()
        countdownJob =
            viewModelScope.launch {
                countdownSeconds(retryAfterMillis, elapsedRealtimeMillis) { remainingSeconds ->
                    state.update { it.copy(error = PasscodeEntryError.LockedOut(remainingSeconds)) }
                }
                state.update { it.copy(error = null) }
            }
    }

    /** Sends the user back to proving the current passcode, discarding what they chose so far. */
    private fun restartFromCurrentStep() {
        newPasscode = null
        textFieldState.clearText()
        state.update { it.copy(step = PasscodeEntryStep.Current, error = null) }
    }

    private suspend fun close() {
        clearSecrets()
        navigator.back()
    }

    /**
     * Drops the plaintext passcodes as soon as they are no longer needed. Kotlin strings cannot be
     * zeroed, so releasing the references promptly is the only lever available: it shortens the
     * window in which a heap dump of the process contains the passcode.
     */
    private fun clearSecrets() {
        currentPasscode = null
        newPasscode = null
        textFieldState.clearText()
    }

    override fun onCleared() {
        clearSecrets()
        super.onCleared()
    }
}
