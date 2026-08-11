package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Whether the passcode gate is covering the app. Provided over the content it covers.
 *
 * Open by default, so a preview or a test composes as it always did.
 */
internal val LocalIsGateClosed = compositionLocalOf { false }

/**
 * Holds [content] back until the passcode gate is open.
 *
 * For content that draws in a window of its own — a `ModalBottomSheet`, an `AlertDialog` — and is
 * raised by background work rather than by a tap: one activity's windows stack in the order they
 * were added, so a window opened after the lock's draws above it however opaque the lock is. A tap
 * is the one thing that cannot land while the lock holds focus.
 *
 * Latched: once composed, [content] stays through a later lock, whose window covers it anyway.
 * Callers keep their own condition around this, so one that lapses while the gate is closed raises
 * nothing once it opens.
 */
@Composable
internal fun OnceUnlocked(content: @Composable () -> Unit) {
    val isGateClosed = LocalIsGateClosed.current
    var isReleased by remember { mutableStateOf(!isGateClosed) }
    LaunchedEffect(isGateClosed) { if (!isGateClosed) isReleased = true }

    if (isReleased) {
        content()
    }
}
