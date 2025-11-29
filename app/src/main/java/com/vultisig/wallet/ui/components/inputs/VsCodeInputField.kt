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
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.v2.utils.toPx
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.textAsFlow
import kotlin.math.roundToInt

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

        val boxSize = remember { mutableIntStateOf(0) }
        val density = LocalDensity.current

        val minSizePx = 46.dp.toPx().roundToInt()
        val paddingPx = 24.dp.toPx().roundToInt()

        val onSizeChanged: (IntSize) -> Unit = remember(density) {
            { size ->
                val maxDimension = maxOf(size.width, size.height)
                val totalSize = maxDimension + paddingPx
                val newSize = maxOf(totalSize, minSizePx)
                boxSize.intValue = maxOf(newSize, boxSize.intValue)
            }
        }

        val boxModifier = remember(boxSize.intValue, density) {
            if (boxSize.intValue > 0) {
                Modifier.size(with(density) {
                    boxSize.intValue.toDp()
                })
            } else {
                Modifier.wrapContentSize()
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(maxCharacters) { index ->
                val isActiveBox = focusedState.value && index == value.length

                val inputBoxShape = RoundedCornerShape(12.dp)
                val inputManager = LocalSoftwareKeyboardController.current

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = boxModifier
                        .background(
                            color = when {
                                value.length <= index -> Theme.v2.colors.backgrounds.secondary
                                else -> when (state) {
                                    VsCodeInputFieldState.Default -> Theme.v2.colors.backgrounds.secondary
                                    VsCodeInputFieldState.Success -> Theme.v2.colors.backgrounds.success
                                    VsCodeInputFieldState.Error -> Theme.v2.colors.backgrounds.error
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
                                isActiveBox -> Theme.v2.colors.border.normal
                                value.length <= index -> Theme.v2.colors.border.light
                                else -> when (state) {
                                    VsCodeInputFieldState.Default -> Theme.v2.colors.border.light
                                    VsCodeInputFieldState.Success -> Theme.v2.colors.alerts.success
                                    VsCodeInputFieldState.Error -> Theme.v2.colors.alerts.error
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
                        color = Theme.v2.colors.text.primary,
                        style = Theme.brockmann.body.m.medium,
                        modifier = Modifier
                            .padding(all = 12.dp)
                            .onSizeChanged(onSizeChanged),
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