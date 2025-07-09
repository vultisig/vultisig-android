package com.vultisig.wallet.ui.components.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.textAsFlow

private const val FAST_VAULT_VERIFICATION_CODE_CHARS = 4

internal enum class VsCodeInputFieldState {
    Default, Success, Error
}

@Composable
internal fun VsCodeInputField(
    textFieldState: TextFieldState,
    onChangeInput: (String) -> Unit,
    modifier: Modifier = Modifier,
    onKeyboardAction: KeyboardActionHandler? = null,
    maxCharacters: Int = FAST_VAULT_VERIFICATION_CODE_CHARS,
    state: VsCodeInputFieldState = VsCodeInputFieldState.Default,
) {
    val focusedState = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        textFieldState.textAsFlow().collect {
            onChangeInput(it.toString())
        }
    }

    Box(
        modifier = modifier,
    ) {
        BasicTextField(
            state = textFieldState,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Go,
            ),
            inputTransformation = InputTransformation
                .maxLength(maxCharacters)
                .then {
                    val length = length
                    if (length >= maxCharacters) {
                        delete(maxCharacters - 1, length - 1)
                    }
                },
            onKeyboardAction = onKeyboardAction,
            modifier = Modifier
                .alpha(0.01f)
                .onFocusChanged {
                    focusedState.value = it.isFocused
                }
                .focusRequester(focusRequester)
                .testTag(CODE_INPUT_FIELD_TAG),
            textStyle = TextStyle.Default.copy(color = Color.Transparent),
        )

        val value = textFieldState.text

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(maxCharacters) { index ->
                val isActiveBox = focusedState.value && index == value.length

                val inputBoxShape = RoundedCornerShape(12.dp)
                var inputManager= LocalSoftwareKeyboardController.current
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            color = when {
                                value.length <= index   ->Theme.colors.backgrounds.secondary
                                else -> when (state) {
                                    VsCodeInputFieldState.Default -> Theme.colors.backgrounds.secondary
                                    VsCodeInputFieldState.Success -> Theme.colors.backgrounds.success
                                    VsCodeInputFieldState.Error -> Theme.colors.backgrounds.error
                                }
                            },
                            shape = inputBoxShape,
                        )
                        .border(
                            width = when {
                                isActiveBox -> 1.5.dp
                                else -> 1.dp
                            },
                            color = when {
                                isActiveBox -> Theme.colors.borders.normal
                                value.length <= index -> Theme.colors.borders.light
                                else -> when (state) {
                                    VsCodeInputFieldState.Default -> Theme.colors.borders.light
                                    VsCodeInputFieldState.Success -> Theme.colors.alerts.success
                                    VsCodeInputFieldState.Error -> Theme.colors.alerts.error
                                }
                            },
                            shape = inputBoxShape,
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            focusRequester.requestFocus()
                            inputManager?.show()
                        },
                ) {
                    val displayChar = value.getOrElse(index) { ' ' }

                    Text(
                        text = displayChar.toString(),
                        color = Theme.colors.text.primary,
                        style = Theme.brockmann.body.m.medium,
                        modifier = Modifier
                            .padding(all = 12.dp),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun VsCodeInputFieldPreview() {
    VsCodeInputField(
        textFieldState = TextFieldState(),
        onChangeInput = {},
    )
}

internal const val CODE_INPUT_FIELD_TAG = "codeInputField"