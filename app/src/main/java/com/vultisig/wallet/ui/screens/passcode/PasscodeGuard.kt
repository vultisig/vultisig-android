package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.ui.components.BiometryAuthScreen
import com.vultisig.wallet.ui.models.passcode.PasscodeGuardViewModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * The app's entry gate.
 *
 * A configured passcode takes over from the device-credential prompt entirely rather than stacking
 * on top of it — two consecutive unlocks to open the app would be a worse experience than either
 * alone, and the passcode is the stronger gate because it also holds the key to the encrypted
 * keyshares.
 *
 * @param onLocked invoked when the gate closes, so the host can clear anything drawing in its own
 *   window. This composable is drawn inside the activity's window, and a `dialog<>` destination is
 *   not: without this, locking with a bottom sheet open leaves that sheet on top of the lock
 *   screen, visible and still taking input.
 */
@Composable
internal fun PasscodeGuard(
    onLocked: () -> Unit = {},
    model: PasscodeGuardViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()

    val isGateClosed =
        state.passcodeState == PasscodeState.Locked ||
            state.passcodeState == PasscodeState.KeyUnavailable
    LaunchedEffect(isGateClosed) { if (isGateClosed) onLocked() }

    when (state.passcodeState) {
        // Persisted state is still loading. An opaque cover, not nothing: the alternative is a
        // frame or two of unlocked content before the lock screen appears.
        PasscodeState.Unknown ->
            Box(
                Modifier.fillMaxSize()
                    .background(Theme.v2.colors.backgrounds.primary)
                    .swallowPointerInput()
            )
        PasscodeState.Locked ->
            Box(Modifier.fillMaxSize()) {
                // Drawn under the lock screen, so the prompt keeps its own taps while anything
                // aimed past it is swallowed here rather than reaching the navigation content
                // this gate is composed over.
                Spacer(Modifier.fillMaxSize().swallowPointerInput())
                PasscodeLockScreen()
            }
        PasscodeState.KeyUnavailable -> KeyUnavailableScreen()
        PasscodeState.Unlocked -> Unit
        // The passcode was just turned off, so the user has already identified themselves this
        // session; sending them straight into a device-credential prompt would be a non sequitur.
        PasscodeState.Disabled -> if (!state.isIdentityProven) BiometryAuthScreen()
    }
}

/**
 * Shown when encrypted keyshares outlived the credentials that opened them — see
 * [PasscodeState.KeyUnavailable]. There is no way back on this device, so the screen says so
 * plainly rather than presenting a passcode prompt that could never succeed.
 */
@Composable
private fun KeyUnavailableScreen() {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .swallowPointerInput(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.passcode_key_unavailable_title),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.headings.title2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.passcode_key_unavailable_message),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.footnote,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
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
