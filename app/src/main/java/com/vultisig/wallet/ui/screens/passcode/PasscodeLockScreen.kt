package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.BiometricUnlockAvailability
import com.vultisig.wallet.ui.components.biometricUnlockAvailability
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.inputs.PasscodeInputField
import com.vultisig.wallet.ui.components.inputs.PasscodeInputFieldState
import com.vultisig.wallet.ui.components.rememberBiometricUnlockLauncher
import com.vultisig.wallet.ui.models.passcode.PasscodeLockError
import com.vultisig.wallet.ui.models.passcode.PasscodeLockUiModel
import com.vultisig.wallet.ui.models.passcode.PasscodeLockViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2

/** Full-screen gate shown whenever the app is locked behind a passcode. */
@Composable
internal fun PasscodeLockScreen(model: PasscodeLockViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberBiometricUnlockLauncher()

    // A copy can outlive the hardware's willingness to release it — biometrics switched off in
    // device settings, or a sensor locked out. Asking here keeps a link off the screen that could
    // only ever answer with an error.
    var isBiometricAvailable by
        remember(context) {
            mutableStateOf(
                context.biometricUnlockAvailability() == BiometricUnlockAvailability.Available
            )
        }

    // And a lockout expires, or the user switches biometrics back on, while this screen is still
    // the one on top. Asking again on the way back keeps the link in step with the hardware.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isBiometricAvailable =
            context.biometricUnlockAvailability() == BiometricUnlockAvailability.Available
    }

    PasscodeLockScreen(
        state = state,
        textFieldState = model.textFieldState,
        isBiometricUnlockAvailable = isBiometricAvailable,
        onUseBiometricsClick = { model.onUseBiometricsClick(launcher) },
    )
}

@Composable
internal fun PasscodeLockScreen(
    state: PasscodeLockUiModel,
    textFieldState: TextFieldState,
    isBiometricUnlockAvailable: Boolean = false,
    onUseBiometricsClick: () -> Unit = {},
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .glow()
                // The numeric keypad opens the moment this screen appears and covers roughly a
                // third of the display. Insetting by it shrinks the centring box to what is
                // actually visible, so the prompt re-centres above the keyboard instead of staying
                // put and being half-covered.
                .imePadding(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.widthIn(max = CONTENT_MAX_WIDTH)
                    .padding(horizontal = 16.dp)
                    // Figma centres the block 28dp above the frame's midpoint; doubling that as
                    // bottom padding shifts the centred content up by exactly that much.
                    .padding(bottom = FIGMA_CENTRE_OFFSET * 2),
        ) {
            Text(
                text = stringResource(R.string.passcode_lock_title),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.headings.largeTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.passcode_lock_subtitle),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.footnote,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(top = 36.dp)
                        .background(color = CardBackground, shape = CardShape)
                        .border(width = 1.dp, color = CardBorder, shape = CardShape)
                        .padding(26.dp),
            ) {
                PasscodeInputField(
                    textFieldState = textFieldState,
                    enabled = state.isInputEnabled,
                    state =
                        if (state.error != null) PasscodeInputFieldState.Error
                        else PasscodeInputFieldState.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let { error ->
                Text(
                    text = error.message(),
                    color = Theme.v2.colors.alerts.error,
                    style = Theme.brockmann.supplementary.footnote,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }

            if (state.isBiometricUnlockEnabled && isBiometricUnlockAvailable) {
                UseBiometricsLink(onClick = onUseBiometricsClick)
            }
        }
    }
}

/**
 * The shortcut, offered and never taken on its own.
 *
 * Deliberately a link rather than a button: it is the secondary way in, and the passcode field
 * above it is the primary one. It stays tappable through a lockout — the throttle is there to slow
 * down guessing at six digits, and a biometric match is not a guess.
 */
@Composable
private fun UseBiometricsLink(onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 36.dp).clickOnce(onClick = onClick),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_biometrics),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )

        Text(
            text = stringResource(R.string.passcode_lock_use_biometrics),
            color = Theme.v2.colors.alerts.info,
            style = Theme.brockmann.button.medium.medium,
        )
    }
}

@Composable
private fun PasscodeLockError.message(): String =
    when (this) {
        is PasscodeLockError.Wrong -> wrongPasscodeMessage(remainingAttempts)
        is PasscodeLockError.LockedOut -> lockedOutMessage(remainingSeconds)
        is PasscodeLockError.NotDigits -> stringResource(R.string.passcode_not_digits)
        is PasscodeLockError.BiometricUnavailable ->
            stringResource(R.string.passcode_biometric_unavailable)
    }

/**
 * The design's backdrop: a wide, soft radial wash with a barely-there ring at its edge, centred
 * above the content rather than on it.
 */
private fun Modifier.glow(): Modifier = drawWithCache {
    val radius = size.width * GLOW_RADIUS_TO_WIDTH
    val center = Offset(x = size.width / 2f, y = size.height * GLOW_CENTER_TO_HEIGHT)
    val wash =
        Brush.radialGradient(
            colors = listOf(GlowInner, GlowOuter),
            center = center,
            radius = radius,
        )
    onDrawBehind {
        drawCircle(brush = wash, radius = radius, center = center)
        drawCircle(
            color = GlowRing,
            radius = radius * GLOW_RING_TO_RADIUS,
            center = center,
            // Density-scaled like the card's 1.dp border, so the ring does not thin out to a
            // hairline on high-density screens.
            style = Stroke(width = GLOW_RING_WIDTH.toPx()),
        )
    }
}

private val CONTENT_MAX_WIDTH = 360.dp
private val GLOW_RING_WIDTH = 1.dp

/** Figma seats the content block this far above the frame's vertical midpoint. */
private val FIGMA_CENTRE_OFFSET = 28.dp
private val CardShape = V2.radius.xl

/** Figma tints the card with 10% of the normal border blue; there is no background token for it. */
private val CardBackground = Color(0xFF1B3F73).copy(alpha = 0.1f)
private val CardBorder = Color.White.copy(alpha = 0.1f)

private val GlowInner = Color(0xFF0439C7).copy(alpha = 0.29f)
private val GlowOuter = Color(0xFF02122A).copy(alpha = 0f)
private val GlowRing = Color(0xFF33E6BF).copy(alpha = 0.05f)

private const val GLOW_RADIUS_TO_WIDTH = 0.9f
private const val GLOW_CENTER_TO_HEIGHT = 0.577f
private const val GLOW_RING_TO_RADIUS = 0.911f

@Preview
@Composable
private fun PasscodeLockScreenPreview() {
    PasscodeLockScreen(state = PasscodeLockUiModel(), textFieldState = TextFieldState("12"))
}

@Preview
@Composable
private fun PasscodeLockScreenErrorPreview() {
    PasscodeLockScreen(
        state = PasscodeLockUiModel(error = PasscodeLockError.Wrong(remainingAttempts = 2)),
        textFieldState = TextFieldState(),
    )
}
