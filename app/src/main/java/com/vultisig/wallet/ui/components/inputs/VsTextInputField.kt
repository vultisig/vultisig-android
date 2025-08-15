package com.vultisig.wallet.ui.components.inputs

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.cursorBrush

internal sealed interface VsTextInputFieldType {
    data object Text : VsTextInputFieldType

    data class Password(
        val isVisible: Boolean,
        val onVisibilityClick: () -> Unit,
    ) : VsTextInputFieldType

    data class MultiLine(
        val minLines: Int,
        val maxLines: Int = Int.MAX_VALUE,
    ) : VsTextInputFieldType
}

internal enum class VsTextInputFieldInnerState {
    Default,
    Success,
    Error,
}

@Composable
internal fun VsTextInputField(
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    onFocusChanged: ((isFocused: Boolean) -> Unit)? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    type: VsTextInputFieldType = VsTextInputFieldType.Text,
    innerState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
    focusRequester: FocusRequester? = remember { FocusRequester() },
    hint: String? = null,
    footNote: String? = null,
    label: String? = null,
    trailingText: String? = null,
    @DrawableRes trailingIcon: Int? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    @DrawableRes trailingIcon2: Int? = null,
    onTrailingIcon2Click: (() -> Unit)? = null,
    @DrawableRes labelIcon: Int? = null,
    onKeyboardAction: KeyboardActionHandler? = null,
    imeAction: ImeAction = ImeAction.Unspecified,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
    enabled: Boolean = true,
) {
    var focused by remember {
        mutableStateOf(false)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .animateContentSize()
            .clickable(onClick = { focusRequester?.requestFocus() }),
    ) {
        if (label != null || labelIcon != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        color = Theme.colors.text.primary,
                        style = Theme.brockmann.body.s.medium,
                    )
                }
                if (labelIcon != null) {
                    Icon(
                        painter = painterResource(labelIcon),
                        tint = Theme.colors.text.extraLight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        val textFieldBackgroundShape = RoundedCornerShape(12.dp)

        Row(
            modifier = Modifier
                .border(
                    border = BorderStroke(
                        width = 1.dp,
                        color = when (innerState) {
                            VsTextInputFieldInnerState.Success -> Theme.colors.alerts.success
                            VsTextInputFieldInnerState.Error -> Theme.colors.alerts.error
                            VsTextInputFieldInnerState.Default ->
                                if (focused) Theme.colors.borders.normal
                                else Theme.colors.borders.light
                        }
                    ),
                    shape = textFieldBackgroundShape,
                )
                .clip(textFieldBackgroundShape)
                .background(Theme.colors.backgrounds.secondary)
                .padding(all = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            val inputTextStyle = Theme.brockmann.body.m.medium.copy(
                color = Theme.colors.text.primary
            )

            when (type) {
                is VsTextInputFieldType.Password -> {
                    BasicSecureTextField(
                        state = textFieldState,
                        textStyle = inputTextStyle,
                        cursorBrush = Theme.cursorBrush,
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = imeAction
                        ),
                        onKeyboardAction = onKeyboardAction,
                        textObfuscationMode = if (type.isVisible)
                            TextObfuscationMode.Visible
                        else TextObfuscationMode.RevealLastTyped,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (focusRequester != null)
                                    Modifier.focusRequester(focusRequester)
                                else Modifier
                            )
                            .then(
                                if (onKeyEvent != null)
                                    Modifier.onKeyEvent(onKeyEvent)
                                else Modifier
                            )
                            .onFocusChanged {
                                focused = it.isFocused
                                onFocusChanged?.invoke(it.isFocused)
                            }
                            .testTag(TEXT_INPUT_FIELD_TAG),
                        decorator = { textField ->
                            TextInputFieldHint(
                                textFieldState = textFieldState,
                                hint = hint,
                                modifier = Modifier
                                    .weight(1f),
                            )
                            textField()
                        }
                    )

                    if (textFieldState.text.isNotEmpty()) {
                        Icon(
                            painter = painterResource(
                                if (type.isVisible)
                                    R.drawable.visible else R.drawable.hidden
                            ),
                            tint = Theme.colors.text.button.light,
                            contentDescription = null,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    onClick = type.onVisibilityClick
                                )
                        )
                    }
                }

                else -> {
                    BasicTextField(
                        state = textFieldState,
                        lineLimits = if (type is VsTextInputFieldType.MultiLine)
                            TextFieldLineLimits.MultiLine(
                                type.minLines,
                                type.maxLines
                            )
                        else TextFieldLineLimits.SingleLine,
                        textStyle = inputTextStyle,
                        cursorBrush = Theme.cursorBrush,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardType,
                            imeAction = imeAction,
                        ),
                        onKeyboardAction = onKeyboardAction,
                        modifier = Modifier
                            .testTag(TEXT_INPUT_FIELD_TAG)
                            .weight(1f)
                            .then(
                                if (focusRequester != null)
                                    Modifier.focusRequester(focusRequester)
                                else Modifier
                            )
                            .then(
                                if (onKeyEvent != null)
                                    Modifier.onKeyEvent(onKeyEvent)
                                else Modifier
                            )
                            .onFocusChanged {
                                focused = it.isFocused
                                onFocusChanged?.invoke(it.isFocused)
                            },
                        decorator = { textField ->
                            TextInputFieldHint(
                                textFieldState = textFieldState,
                                hint = hint,
                            )
                            textField()
                        },
                        enabled = enabled,
                    )

                    if (type !is VsTextInputFieldType.MultiLine) {
                        if (trailingText != null) {
                            Text(
                                text = trailingText,
                                color = Theme.colors.text.light,
                                style = Theme.brockmann.body.s.medium,
                            )
                        }

                        if (trailingIcon != null) {
                            UiSpacer(8.dp)
                            Icon(
                                painter = painterResource(trailingIcon),
                                tint = Theme.colors.text.button.light,
                                contentDescription = null,
                                modifier = Modifier
                                    .width(16.dp)
                                    .clickOnce(
                                        onClick = onTrailingIconClick ?: {},
                                        enabled = onTrailingIconClick != null
                                    )
                            )
                        }

                        if (trailingIcon2 != null) {
                            UiSpacer(8.dp)
                            Icon(
                                painter = painterResource(trailingIcon2),
                                tint = Theme.colors.text.button.light,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickOnce(
                                        onClick = onTrailingIcon2Click ?: {},
                                        enabled = onTrailingIcon2Click != null
                                    )
                            )
                        }
                    }
                }
            }
        }

        if (footNote != null) {
            Text(
                text = footNote,
                color = when (innerState) {
                    VsTextInputFieldInnerState.Success -> Theme.colors.alerts.success
                    VsTextInputFieldInnerState.Error -> Theme.colors.alerts.error
                    VsTextInputFieldInnerState.Default -> Theme.colors.text.primary
                },
                style = Theme.brockmann.supplementary.footnote
            )
        }
    }
}

@Composable
private fun TextInputFieldHint(
    textFieldState: TextFieldState,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    if (textFieldState.text.isEmpty() && hint != null) {
        Text(
            text = hint,
            color = Theme.colors.text.extraLight,
            style = Theme.brockmann.body.m.medium,
            modifier = modifier,
        )
    }
}


@Preview(widthDp = 400)
@Composable
private fun TextTypePreview() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Text,
            innerState = VsTextInputFieldInnerState.Default,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Text,
            innerState = VsTextInputFieldInnerState.Default,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Text,
            innerState = VsTextInputFieldInnerState.Success,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Text,
            innerState = VsTextInputFieldInnerState.Error,
        )
    }
}

@Preview(widthDp = 400)
@Composable
private fun PasswordTypePreview() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Password(isVisible = true) {},
            innerState = VsTextInputFieldInnerState.Default,
            initialText = "some password"
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Password(isVisible = false) {},
            innerState = VsTextInputFieldInnerState.Default,
            initialText = "some password"
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Password(isVisible = true) {},
            innerState = VsTextInputFieldInnerState.Default,
            initialText = ""
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Password(isVisible = true) {},
            innerState = VsTextInputFieldInnerState.Success,
            initialText = "some password"
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Password(isVisible = false) {},
            innerState = VsTextInputFieldInnerState.Error,
            initialText = "some password"
        )
    }
}

@Preview(widthDp = 400)
@Composable
private fun MultiLineTypePreview() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.MultiLine(minLines = 5),
            innerState = VsTextInputFieldInnerState.Default,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.MultiLine(minLines = 5),
            innerState = VsTextInputFieldInnerState.Default,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.MultiLine(minLines = 5),
            innerState = VsTextInputFieldInnerState.Success,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.MultiLine(minLines = 5),
            innerState = VsTextInputFieldInnerState.Error,
        )
    }
}

@Composable
private fun VsTextInputPreviewMaker(
    type: VsTextInputFieldType,
    innerState: VsTextInputFieldInnerState,
    initialText: String = "",
) {
    VsTextInputField(
        modifier = Modifier,
        label = "Label",
        hint = "hint",
        labelIcon = R.drawable.ic_question_mark,
        textFieldState = rememberTextFieldState(initialText),
        onFocusChanged = {},
        type = type,
        innerState = innerState,
        footNote = "foot note",
        trailingText = "trail",
        trailingIcon = R.drawable.question,
        onTrailingIconClick = {},
        trailingIcon2 = R.drawable.camera,
        onTrailingIcon2Click = {},
        onKeyboardAction = {},
        imeAction = ImeAction.Done,
        keyboardType = KeyboardType.Number,
    )
}



internal const val TEXT_INPUT_FIELD_TAG = "textInputField"

