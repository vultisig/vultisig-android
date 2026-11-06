package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.PASSCODE_LENGTH
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.drawSoftGlow
import com.vultisig.wallet.ui.components.inputs.PasscodeDots
import com.vultisig.wallet.ui.components.inputs.PasscodeInputField
import com.vultisig.wallet.ui.components.inputs.PasscodeInputFieldState
import com.vultisig.wallet.ui.components.inputs.PasscodeInputFieldStyle
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.passcode.PasscodeEntryError
import com.vultisig.wallet.ui.models.passcode.PasscodeEntryStep
import com.vultisig.wallet.ui.models.passcode.PasscodeEntryUiModel
import com.vultisig.wallet.ui.models.passcode.PasscodeEntryViewModel
import com.vultisig.wallet.ui.navigation.Route.PasscodeEntryAction
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2

private val BLOCK_TOP_GAP = 106.dp
private val CAPTION_MAX_WIDTH = 283.dp
private val GLOW_CENTER_Y = 275.dp
private val GLOW_RADIUS = 400.dp
private val GLOW_COLOR = V2.colors.primary.accent4
private val GLOW_STOPS =
    arrayOf(
        0f to GLOW_COLOR.copy(alpha = 0.11f),
        0.2f to GLOW_COLOR.copy(alpha = 0.096f),
        0.4f to GLOW_COLOR.copy(alpha = 0.062f),
        0.6f to GLOW_COLOR.copy(alpha = 0.031f),
        0.8f to GLOW_COLOR.copy(alpha = 0.011f),
        1f to Color.Transparent,
    )

@Composable
internal fun PasscodeEntryScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<PasscodeEntryViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    PasscodeEntryScreen(
        state = state,
        textFieldState = viewModel.textFieldState,
        onBackClick = { navController.popBackStack() },
    )
}

@Composable
private fun PasscodeEntryScreen(
    state: PasscodeEntryUiModel,
    textFieldState: TextFieldState,
    onBackClick: () -> Unit,
) {
    V2Scaffold(title = stringResource(state.action.titleRes()), onBackClick = onBackClick) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().imePadding().passcodeEntryGlow()) {
            val density = LocalDensity.current
            val scrollState = rememberScrollState()
            var blockHeight by remember { mutableStateOf(0.dp) }
            // The numeric keypad covers a third of the screen, so the block gives up its top gap
            // as it needs the room, measured so font scale and long locales count.
            val topGap = (maxHeight - blockHeight).coerceIn(0.dp, BLOCK_TOP_GAP)

            // Past that it scrolls, and the live row and any error are last, so stay on them.
            LaunchedEffect(scrollState.maxValue) { scrollState.scrollTo(scrollState.maxValue) }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(top = topGap)
                        .onSizeChanged { blockHeight = with(density) { it.height.toDp() } },
            ) {
                PasscodeEntryBlock(state = state, textFieldState = textFieldState)
            }
        }
    }
}

@Composable
private fun PasscodeEntryBlock(state: PasscodeEntryUiModel, textFieldState: TextFieldState) {
    val isConfirmStep = state.step == PasscodeEntryStep.Confirm

    UiIcon(
        drawableResId = R.drawable.ic_lock_password,
        size = 46.dp,
        tint = Theme.v2.colors.primary.accent4,
    )

    Text(
        text =
            stringResource(
                if (isConfirmStep) PasscodeEntryStep.New.headlineRes()
                else state.step.headlineRes()
            ),
        color = Theme.v2.colors.text.primary,
        style = Theme.brockmann.headings.title3,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    )

    state.step.caption(state.action)?.let { caption ->
        Text(
            text = caption,
            color = Theme.v2.colors.text.secondary,
            style = Theme.brockmann.body.s.regular,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = CAPTION_MAX_WIDTH).padding(top = 14.dp),
        )
    }

    if (isConfirmStep) {
        // The first row is already accepted by this step, so it is always full.
        PasscodeDots(
            filledCount = PASSCODE_LENGTH,
            isError = false,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(PasscodeEntryStep.Confirm.headlineRes()),
            color = Theme.v2.colors.text.secondary,
            style = Theme.brockmann.body.s.regular,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
    }

    PasscodeInputField(
        textFieldState = textFieldState,
        enabled = state.isInputEnabled,
        state =
            if (state.error != null) PasscodeInputFieldState.Error
            else PasscodeInputFieldState.Default,
        modifier =
            Modifier.fillMaxWidth().padding(top = if (isConfirmStep) 16.dp else 24.dp),
        style = PasscodeInputFieldStyle.Dots,
    )

    state.error?.let { error ->
        Text(
            text = error.message(),
            color = Theme.v2.colors.alerts.error,
            style = Theme.brockmann.supplementary.footnote,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

/** The blue wash Figma lays behind the block, a blurred ellipse. */
private fun Modifier.passcodeEntryGlow(): Modifier = drawBehind {
    drawSoftGlow(
        glowCenter = Offset(center.x, GLOW_CENTER_Y.toPx()),
        radius = GLOW_RADIUS.toPx(),
        stops = GLOW_STOPS,
    )
}

private fun PasscodeEntryAction.titleRes(): Int =
    when (this) {
        PasscodeEntryAction.Set -> R.string.passcode_entry_title_set
        PasscodeEntryAction.Change -> R.string.passcode_entry_title_change
        PasscodeEntryAction.Disable -> R.string.passcode_entry_title_disable
    }

private fun PasscodeEntryStep.headlineRes(): Int =
    when (this) {
        PasscodeEntryStep.Current -> R.string.passcode_entry_headline_current
        PasscodeEntryStep.New -> R.string.passcode_entry_headline_new
        PasscodeEntryStep.Confirm -> R.string.passcode_entry_headline_confirm
    }

@Composable
private fun PasscodeEntryStep.caption(action: PasscodeEntryAction): String? =
    when (this) {
        PasscodeEntryStep.Current if action == PasscodeEntryAction.Disable ->
            stringResource(R.string.passcode_entry_caption_current_disable)
        PasscodeEntryStep.Current -> stringResource(R.string.passcode_entry_caption_current_change)
        // The digit count comes from the constant the input field and validation share, so the copy
        // cannot drift from the length actually enforced.
        PasscodeEntryStep.New ->
            stringResource(R.string.passcode_entry_caption_new, PASSCODE_LENGTH)
        PasscodeEntryStep.Confirm -> null
    }

@Composable
private fun PasscodeEntryError.message(): String =
    when (this) {
        is PasscodeEntryError.Wrong -> wrongPasscodeMessage(remainingAttempts)
        is PasscodeEntryError.LockedOut -> lockedOutMessage(remainingSeconds)
        is PasscodeEntryError.Mismatch -> stringResource(R.string.passcode_entry_mismatch)
        is PasscodeEntryError.OperationFailed -> stringResource(R.string.passcode_operation_failed)
        is PasscodeEntryError.NotDigits -> stringResource(R.string.passcode_not_digits)
    }

@Preview
@Composable
private fun PasscodeEntryScreenPreview() {
    PasscodeEntryScreen(
        state =
            PasscodeEntryUiModel(
                action = PasscodeEntryAction.Set,
                step = PasscodeEntryStep.Confirm,
                error = PasscodeEntryError.Mismatch,
            ),
        textFieldState = TextFieldState(),
        onBackClick = {},
    )
}
