package com.vultisig.wallet.ui.components.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.PASSCODE_LENGTH
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2

/** Whether the field is showing a plain entry or flagging a rejected passcode. */
internal enum class PasscodeInputFieldState {
    Default,
    Error,
}

/**
 * One cell per [PASSCODE_LENGTH] digit.
 *
 * Follows the same mechanic as [VsCodeInputField] — a 1dp invisible [BasicTextField] owns the input
 * and keyboard while the visible cells are drawn from its text — but masks what it renders. This is
 * a passcode over the user's vaults, so the digits are never shown, not even transiently.
 */
@Composable
internal fun PasscodeInputField(
    textFieldState: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: PasscodeInputFieldState = PasscodeInputFieldState.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(enabled) { if (enabled) focusRequester.requestFocus() }

    Box(modifier = modifier) {
        BasicTextField(
            state = textFieldState,
            enabled = enabled,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Go,
                ),
            inputTransformation = InputTransformation.maxLength(PASSCODE_LENGTH),
            onKeyboardAction = onKeyboardAction,
            modifier =
                Modifier.size(1.dp)
                    .alpha(0.01f)
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusRequester(focusRequester)
                    .testTag(PASSCODE_INPUT_FIELD_TAG),
            // Belt and braces with the masking below: even the off-screen field renders nothing.
            textStyle = TextStyle.Default.copy(color = Color.Transparent),
        )

        val entered = textFieldState.text.length

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier.height(PASSCODE_CELL_HEIGHT).clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                ) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                },
        ) {
            repeat(PASSCODE_LENGTH) { index ->
                val isActive = enabled && isFocused && index == entered
                val isFilled = index < entered

                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier.weight(1f)
                            .height(PASSCODE_CELL_HEIGHT)
                            .background(
                                color = Theme.v2.colors.backgrounds.surface1,
                                shape = PasscodeCellShape,
                            )
                            .border(
                                width = 1.dp,
                                color =
                                    when {
                                        state == PasscodeInputFieldState.Error ->
                                            Theme.v2.colors.alerts.error
                                        isActive -> Theme.v2.colors.border.light
                                        else -> PasscodeCellBorder
                                    },
                                shape = PasscodeCellShape,
                            ),
                ) {
                    when {
                        isFilled ->
                            Text(
                                text = FILLED_MARK,
                                color = Theme.v2.colors.text.primary,
                                style = Theme.brockmann.body.m.medium,
                            )
                        isActive ->
                            Text(
                                text = CARET_MARK,
                                color = Theme.v2.colors.primary.accent4,
                                style = Theme.brockmann.body.m.medium,
                            )
                    }
                }
            }
        }

        // One description for the whole row: announcing identical cells adds nothing, whereas how
        // many digits are in so far is the part a screen-reader user cannot see.
        val enteredDescription =
            stringResource(
                R.string.passcode_input_field_content_description,
                entered,
                PASSCODE_LENGTH,
            )
        Box(modifier = Modifier.size(1.dp).semantics { contentDescription = enteredDescription })
    }
}

private val PASSCODE_CELL_HEIGHT = 51.dp
private val PasscodeCellShape = V2.radius.md

/** Figma uses a flat 10% white hairline on the idle cells rather than a theme border token. */
private val PasscodeCellBorder = Color.White.copy(alpha = 0.1f)

private const val FILLED_MARK = "•"
private const val CARET_MARK = "|"

internal const val PASSCODE_INPUT_FIELD_TAG = "passcodeInputField"

@Preview
@Composable
private fun PasscodeInputFieldPreview() {
    PasscodeInputField(textFieldState = remember { TextFieldState("12") })
}
