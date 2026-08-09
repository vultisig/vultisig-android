package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vultisig.wallet.R
import kotlin.math.ceil

/**
 * The wording for a rejected passcode entry, shared by the lock screen and the settings prompts.
 *
 * Both surfaces rejected entries with the same rules and, until this existed, with two copies of
 * the same formatter. Copies drift: a fix to one is a bug report against the other.
 */
@Composable
internal fun wrongPasscodeMessage(remainingAttempts: Int): String =
    if (remainingAttempts == 1) stringResource(R.string.passcode_lock_last_attempt)
    else stringResource(R.string.passcode_lock_wrong_passcode)

/** The wording for the throttle countdown, in minutes once it passes a minute. */
@Composable
internal fun lockedOutMessage(remainingSeconds: Long): String =
    if (remainingSeconds >= SECONDS_PER_MINUTE) {
        stringResource(
            R.string.passcode_lock_too_many_attempts_minutes,
            ceil(remainingSeconds / SECONDS_PER_MINUTE.toFloat()).toInt(),
        )
    } else {
        stringResource(R.string.passcode_lock_too_many_attempts_seconds, remainingSeconds.toInt())
    }

private const val SECONDS_PER_MINUTE = 60
