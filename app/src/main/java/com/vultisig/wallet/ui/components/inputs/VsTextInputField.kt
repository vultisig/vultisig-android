package com.vultisig.wallet.ui.components.inputs

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
        val onVisibilityClick: () -> Unit
    ) : VsTextInputFieldType

    data object Number : VsTextInputFieldType
    data class MultiLine(
        val minLines: Int,
        val maxLines: Int = Int.MAX_VALUE
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
    onFocusChanged: (isFocused: Boolean) -> Unit,
    focused: Boolean,
    type: VsTextInputFieldType = VsTextInputFieldType.Text,
    innerState: VsTextInputFieldInnerState = VsTextInputFieldInnerState.Default,
    hint: String? = null,
    footNote: String? = null,
    label: String? = null,
    trailingText: String? = null,
    @DrawableRes trailingIcon: Int? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    @DrawableRes trailingIcon2: Int? = null,
    onTrailingIcon2Click: (() -> Unit)? = null,
    @DrawableRes labelIcon: Int? = null,
    onKeyboardAction: () -> Unit = {},
    imeAction: ImeAction = ImeAction.Unspecified,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
) {
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                    shape = RoundedCornerShape(corner = CornerSize(12.dp))
                )
                .clip(RoundedCornerShape(corner = CornerSize(12.dp)))
                .background(Theme.colors.backgrounds.secondary)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (type) {
                is VsTextInputFieldType.Password -> {
                    BasicSecureTextField(
                        state = textFieldState,
                        textStyle = Theme.brockmann.body.m.medium.copy(
                            color = Theme.colors.text.primary
                        ),
                        cursorBrush = Theme.cursorBrush,
                        textObfuscationMode = if (type.isVisible)
                            TextObfuscationMode.RevealLastTyped else TextObfuscationMode.Visible,
                        modifier = Modifier
                            .onFocusChanged {
                                onFocusChanged(it.isFocused)
                            },
                        decorator = { textField ->
                            if (textFieldState.text.isEmpty() && hint != null) {
                                Text(
                                    text = hint,
                                    color = Theme.colors.text.extraLight,
                                    style = Theme.brockmann.body.m.medium,
                                )
                            }
                            textField()
                        }
                    )
                    UiSpacer(1f)
                    if(textFieldState.text.isNotEmpty()) {
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
                            ) else TextFieldLineLimits.SingleLine,
                        textStyle = Theme.brockmann.body.m.medium.copy(color = Theme.colors.text.primary),
                        cursorBrush = Theme.cursorBrush,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (type is VsTextInputFieldType.Number)
                                KeyboardType.Number else keyboardType,
                            imeAction = imeAction,
                        ),
                        onKeyboardAction = {
                            onKeyboardAction()
                        },
                        modifier = Modifier
                            .then(
                                if (type !is VsTextInputFieldType.Number)
                                    Modifier.weight(1f)
                                else {
                                    Modifier.width(IntrinsicSize.Min)
                                }
                            )
                            .onFocusChanged {
                                onFocusChanged(it.isFocused)
                            },
                        decorator = { textField ->
                            if (textFieldState.text.isEmpty() && hint != null) {
                                Text(
                                    text = hint,
                                    color = Theme.colors.text.extraLight,
                                    style = Theme.brockmann.body.m.medium,
                                )
                            }
                            textField()
                        })

                    if (type !is VsTextInputFieldType.MultiLine) {
                        if (trailingText != null)
                            Text(
                                text = trailingText,
                                color = Theme.colors.text.light,
                                style = Theme.brockmann.body.s.medium,
                            )
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


@Preview(widthDp = 400)
@Composable
private fun TextTypePreview() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Text,
            innerState = VsTextInputFieldInnerState.Default,
            focused = false,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Text,
            innerState = VsTextInputFieldInnerState.Default,
            focused = true,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Text,
            innerState = VsTextInputFieldInnerState.Success,
            focused = false,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Text,
            innerState = VsTextInputFieldInnerState.Error,
            focused = false,
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
            focused = false,
            initialText = "some password"
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Password(isVisible = false) {},
            innerState = VsTextInputFieldInnerState.Default,
            focused = true,
            initialText = "some password"
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Password(isVisible = true) {},
            innerState = VsTextInputFieldInnerState.Default,
            focused = false,
            initialText = ""
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Password(isVisible = true) {},
            innerState = VsTextInputFieldInnerState.Success,
            focused = false,
            initialText = "some password"
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.Password(isVisible = false) {},
            innerState = VsTextInputFieldInnerState.Error,
            focused = false,
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
            focused = false,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.MultiLine(minLines = 5),
            innerState = VsTextInputFieldInnerState.Default,
            focused = true,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.MultiLine(minLines = 5),
            innerState = VsTextInputFieldInnerState.Success,
            focused = false,
        )
        VsTextInputPreviewMaker(
            type = VsTextInputFieldType.MultiLine(minLines = 5),
            innerState = VsTextInputFieldInnerState.Error,
            focused = false,
        )
    }
}

@Preview(widthDp = 400)
@Composable
private fun NumberTypePreview() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VsTextInputForNumberPreviewMaker(
            type = VsTextInputFieldType.Number,
            innerState = VsTextInputFieldInnerState.Default,
            focused = false,
        )
        VsTextInputForNumberPreviewMaker(
            type = VsTextInputFieldType.Number,
            innerState = VsTextInputFieldInnerState.Default,
            focused = true,
        )
        VsTextInputForNumberPreviewMaker(
            type = VsTextInputFieldType.Number,
            innerState = VsTextInputFieldInnerState.Success,
            focused = false,
        )
        VsTextInputForNumberPreviewMaker(
            type = VsTextInputFieldType.Number,
            innerState = VsTextInputFieldInnerState.Error,
            focused = false,
        )
    }
}

@Composable
private fun VsTextInputPreviewMaker(
    type: VsTextInputFieldType,
    innerState: VsTextInputFieldInnerState,
    focused: Boolean,
    initialText: String = "",
) {
    VsTextInputField(
        modifier = Modifier,
        label = "Label",
        hint = "hint",
        labelIcon = R.drawable.ic_question_mark,
        textFieldState = rememberTextFieldState(initialText),
        onFocusChanged = {},
        focused = focused,
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

@Composable
private fun VsTextInputForNumberPreviewMaker(
    type: VsTextInputFieldType,
    innerState: VsTextInputFieldInnerState,
    focused: Boolean,
) {
    VsTextInputField(
        modifier = Modifier,
        hint = "hint",
        textFieldState = rememberTextFieldState(),
        onFocusChanged = {},
        focused = focused,
        type = type,
        innerState = innerState,
        footNote = "foot note",
    )
}



