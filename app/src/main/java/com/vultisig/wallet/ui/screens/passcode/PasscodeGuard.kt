package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 */
@Composable
internal fun PasscodeGuard(model: PasscodeGuardViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()

    when (state) {
        // Persisted state is still loading. An opaque cover, not nothing: the alternative is a
        // frame or two of unlocked content before the lock screen appears.
        PasscodeState.Unknown ->
            Box(Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary))
        PasscodeState.Locked -> PasscodeLockScreen()
        PasscodeState.Unlocked -> Unit
        PasscodeState.Disabled -> BiometryAuthScreen()
    }
}
