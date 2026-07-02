package com.vultisig.wallet.ui.screens.keysign

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.rive.Fit
import app.rive.ViewModelSource
import app.rive.rememberViewModelInstance
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.rive.rememberRiveResourceFile
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.delay

/**
 * Full-bleed "waiting to connect" screen shown to a keysign joiner while it discovers the session /
 * waits for the initiator. It plays the shared [R.raw.riv_keysign] animation in its default
 * "Connecting" state (the "Connected" boolean is only flipped later, on the signing-progress
 * screen), mirroring the keygen connecting screen so the same Rive design carries across both
 * flows.
 */
@Composable
internal fun KeysignLoadingScreen(modifier: Modifier = Modifier) {
    KeepScreenOn()

    val riveFile = rememberRiveResourceFile(resId = R.raw.riv_keysign).value

    // Reveal the Rive only after it has had a moment to inflate, so the transition from the
    // preceding screen renders the solid app background rather than an empty/blank frame.
    var showRive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(RIVE_REVEAL_DELAY_MS)
        showRive = true
    }

    Box(modifier = modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)) {
        if (showRive && riveFile != null) {
            val vmi =
                rememberViewModelInstance(
                    file = riveFile,
                    source = ViewModelSource.Named("ViewModel").defaultInstance(),
                )
            RiveAnimation(
                file = riveFile,
                viewModelInstance = vmi,
                modifier = Modifier.fillMaxSize(),
                fit = Fit.Cover(),
            )
        }
    }
}

private const val RIVE_REVEAL_DELAY_MS = 300L
