package com.vultisig.wallet.ui.screens.passcode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.ui.components.BiometryAuthScreen
import com.vultisig.wallet.ui.components.errors.ErrorState
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.errors.ErrorViewButtonUiModel
import com.vultisig.wallet.ui.models.passcode.PasscodeGuardViewModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * The app's entry gate.
 *
 * A configured passcode takes over from the device-credential prompt entirely rather than stacking
 * on top of it — two consecutive unlocks to open the app would be a worse experience than either
 * alone, and the passcode is the stronger gate because it also holds the key to the encrypted
 * keyshares.
 */
@Composable
internal fun PasscodeGuard(model: PasscodeGuardViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()

    // Swallowed rather than passed on. The navigation content behind the gate is still composed
    // and still has its own back handling, so back would walk that graph unseen and leave the user
    // somewhere else entirely once they unlock. This handler is registered after the nav host's,
    // which is what puts it first in line.
    BackHandler(enabled = state.isGateClosed) {}

    if (state.isGateClosed) {
        // Covers the app for the frame between the state that closes the gate and the window below
        // being added, which is the only moment the window cannot cover for itself.
        Box(
            Modifier.fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .swallowPointerInput()
        )

        // One call site for the whole closed gate rather than one per state: a second would be a
        // second composition slot, and moving between them would take the window down and put a new
        // one up at exactly the moment the lock appears.
        LockWindow { GateContent(state.passcodeState, model::onRetry) }
    }

    // The passcode was just turned off, so the user has already identified themselves this session;
    // sending them straight into a device-credential prompt would be a non sequitur.
    if (state.passcodeState == PasscodeState.Disabled && !state.isIdentityProven) {
        BiometryAuthScreen()
    }
}

/** What the gate shows, once there is something to show. */
@Composable
private fun GateContent(passcodeState: PasscodeState, onRetry: () -> Unit) {
    when (passcodeState) {
        PasscodeState.Locked -> PasscodeLockScreen()
        PasscodeState.KeyUnavailable ->
            DeadEndScreen(
                title = stringResource(R.string.passcode_key_unavailable_title),
                message = stringResource(R.string.passcode_key_unavailable_message),
                onRetry = onRetry,
            )
        PasscodeState.StoreUnavailable ->
            DeadEndScreen(
                title = stringResource(R.string.passcode_store_unavailable_title),
                message = stringResource(R.string.passcode_store_unavailable_message),
                onRetry = onRetry,
            )
        // Persisted state is still loading. The window is up regardless, because a dialog
        // destination restored with the back stack opens its own window while this read is still
        // running, and only another window can cover that one.
        PasscodeState.Unknown -> Unit
        // The gate is open in both, so this is not composed.
        PasscodeState.Unlocked,
        PasscodeState.Disabled -> Unit
    }
}

/**
 * Hosts [content] in a window of its own.
 *
 * A `ModalBottomSheet` and a `dialog<>` destination each get a window too, and one activity's
 * windows stack in the order they were added — so nothing drawn in the activity's own window can be
 * above them, however opaque. Adding the lock's window when the app locks is what puts it over a
 * sheet that was already open.
 */
@Composable
private fun LockWindow(content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                // Back and outside taps belong to this window once it is up; declining both leaves
                // no way past it but the state that stops composing it.
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                // Lays the window out over the system bars, so the background below reaches them
                // and the content is inset back off them, as the activity does with its own.
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .safeDrawingPadding()
        ) {
            content()
        }
    }
}

/**
 * Shown when encrypted keyshares cannot be opened at all — the credentials are gone
 * ([PasscodeState.KeyUnavailable]) or unreachable this launch ([PasscodeState.StoreUnavailable]).
 * Neither has a passcode that would work, so the screen says what happened rather than asking for
 * one. The two differ only in what the user should do next, which is exactly what [message]
 * carries.
 *
 * [onRetry] is the only way off this window, since back and outside taps belong to the gate. Both
 * states rest on a keystore read that can come back, so it is worth offering.
 */
@Composable
private fun DeadEndScreen(title: String, message: String, onRetry: () -> Unit) {
    ErrorView(
        title = title,
        description = message,
        errorState = ErrorState.WARNING,
        buttonUiModel =
            ErrorViewButtonUiModel(text = stringResource(R.string.try_again), onClick = onRetry),
    )
}

/** Consumes every pointer event that reaches this node so none of it falls through. */
private fun Modifier.swallowPointerInput(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
