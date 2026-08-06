package com.vultisig.wallet.ui.models.passcode

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.passcode.PASSCODE_LENGTH
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeUnlockResult
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
}

@Immutable
internal data class PasscodeLockUiModel(
    val isVerifying: Boolean = false,
    val error: PasscodeLockError? = null,
) {
    val isInputEnabled: Boolean
        get() = !isVerifying && error !is PasscodeLockError.LockedOut
}

/**
 * Drives the App Locked screen: collects five digits, verifies them, and counts down the throttle
 * when too many were wrong.
 */
@HiltViewModel
internal class PasscodeLockViewModel
@Inject
constructor(private val passcodeRepository: PasscodeRepository) : ViewModel() {

    val textFieldState = TextFieldState()

    val state = MutableStateFlow(PasscodeLockUiModel())

    private var verifyJob: Job? = null
    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            textFieldState.textAsFlow().collect { text ->
                // Clear a stale error as soon as the user starts over, so the screen does not keep
                // accusing them while they type the next attempt.
                if (state.value.error is PasscodeLockError.Wrong) {
                    state.update { it.copy(error = null) }
                }
                if (text.length == PASSCODE_LENGTH) {
                    verify(text.toString())
                }
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
                }
            }
    }

    private fun startCountdown(retryAfterMillis: Long) {
        countdownJob?.cancel()
        countdownJob =
            viewModelScope.launch {
                var remaining = retryAfterMillis.milliseconds
                while (remaining > 0.seconds) {
                    state.update {
                        it.copy(error = PasscodeLockError.LockedOut(remaining.inWholeSeconds))
                    }
                    delay(1.seconds)
                    remaining -= 1.seconds
                }
                state.update { it.copy(error = null) }
            }
    }
}
