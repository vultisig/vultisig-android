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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldVariant.Default
import com.vultisig.wallet.ui.theme.AlertsColors
import com.vultisig.wallet.ui.theme.BordersColors
import com.vultisig.wallet.ui.theme.TextColors
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.cursorBrush
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

internal sealed interface VsTextInputFieldVariant {
    data object Default : VsTextInputFieldVariant

    data class WrapContent(val preferredWidth: Dp? = null) : VsTextInputFieldVariant

    data class Password(
        val isVisible: Boolean,
        val onToggleVisibilityClick: () -> Unit
    ) : VsTextInputFieldVariant

    data class WithAction(
        @DrawableRes val icon: Int,
        val onIconClick: () -> Unit,
        @DrawableRes val icon2: Int? = null,
        val onIcon2Click: (() -> Unit)? = null,
        val text: UiText?
    ) : VsTextInputFieldVariant
}


internal sealed class VsTextInputFieldState(val primaryColor: Color) {
    data object Idle : VsTextInputFieldState(primaryColor = BordersColors().light)
    data object Focus : VsTextInputFieldState(primaryColor = BordersColors().normal)
    data object Success : VsTextInputFieldState(primaryColor = AlertsColors().success)
    data object Error : VsTextInputFieldState(primaryColor = AlertsColors().error)
}

@Composable
internal fun VsTextInputField(
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    variant: VsTextInputFieldVariant,
    uiState: VsTextInputFieldState,
    onFocusChanged: (isFocused: Boolean) -> Unit,
    hint: UiText? = null,
    footNote: UiText? = null,
    label: UiText? = null,
    @DrawableRes labelIcon: Int? = null,
    onKeyboardAction: () -> Unit = {},
    imeAction: ImeAction = ImeAction.Unspecified,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
) {
    val textColors = TextColors()
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
                    text = label.asString(),
                    color = textColors.primary,
                    style = Theme.montserrat.overline,
                )
            }
            if (labelIcon != null) {
                Icon(
                    painter = painterResource(labelIcon),
                    tint = textColors.primary,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .border(
                    border = BorderStroke(
                        width = 1.dp,
                        color = uiState.primaryColor
                    ),
                    shape = RoundedCornerShape(25)
                )
                .clip(RoundedCornerShape(25))
                .background(Theme.colors.oxfordBlue600Main)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (variant is VsTextInputFieldVariant.Password) {
                BasicSecureTextField(
                    state = textFieldState,
                    textStyle = Theme.menlo.body3.copy(color = textColors.primary),
                    cursorBrush = Theme.cursorBrush,
                    textObfuscationMode = if (variant.isVisible)
                        TextObfuscationMode.RevealLastTyped else TextObfuscationMode.Visible,
                    modifier = Modifier
                        .onFocusChanged {
                            onFocusChanged(it.isFocused)
                        },
                    decorator = { textField ->
                        if (textFieldState.text.isEmpty() && hint != null) {
                            Text(
                                text = hint.asString(),
                                color = textColors.extraLight,
                                style = Theme.menlo.body3,
                            )
                        }
                        textField()
                    }
                )
                UiSpacer(1f)
                Icon(
                    painter = painterResource(
                        if (variant.isVisible)
                            R.drawable.visible else R.drawable.hidden
                    ),
                    tint = textColors.light,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            onClick = variant.onToggleVisibilityClick
                        )
                )
            } else {
                BasicTextField(
                    state = textFieldState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = Theme.menlo.body3.copy(color = textColors.primary),
                    cursorBrush = Theme.cursorBrush,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction,
                    ),
                    onKeyboardAction = {
                        onKeyboardAction()
                    },
                    modifier = Modifier
                        .then(
                            if (variant !is VsTextInputFieldVariant.WrapContent)
                                Modifier.weight(1f)
                            else {
                                if (variant.preferredWidth != null)
                                    Modifier.width(variant.preferredWidth)
                                else Modifier.width(IntrinsicSize.Min)
                            }
                        )
                        .onFocusChanged {
                            onFocusChanged(it.isFocused)
                        },
                    decorator = { textField ->
                        if (textFieldState.text.isEmpty() && hint != null) {
                            Text(
                                text = hint.asString(),
                                color = textColors.extraLight,
                                style = Theme.menlo.body3,
                            )
                        }
                        textField()
                    })
            }
            if (variant is VsTextInputFieldVariant.WithAction) {
                if (variant.text != null)
                    Text(
                        text = variant.text.asString(),
                        color = textColors.light,
                        style = Theme.montserrat.overline,
                    )
                UiSpacer(8.dp)
                Icon(
                    painter = painterResource(variant.icon),
                    tint = textColors.light,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = variant.onIconClick)
                )
                if (variant.icon2 != null) {
                    UiSpacer(8.dp)
                    Icon(
                        painter = painterResource(variant.icon2),
                        tint = textColors.light,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                onClick = variant.onIcon2Click ?: {}
                            )
                    )
                }
            }
        }
        if (footNote != null) {
            Text(
                text = footNote.asString(),
                color = uiState.primaryColor,
                style = Theme.montserrat.overline
            )
        }
    }

}


@Preview(widthDp = 400)
@Composable
private fun DefaultVariantPreview() {
    val textFieldState = rememberTextFieldState()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = Default,
            uiState = VsTextInputFieldState.Idle,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = Default,
            uiState = VsTextInputFieldState.Focus,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            footNote = UiText.DynamicString("some message"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = Default,
            uiState = VsTextInputFieldState.Success,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            footNote = UiText.DynamicString("some message"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = Default,
            uiState = VsTextInputFieldState.Error,
            onFocusChanged = {},
        )
    }
}

@Preview(widthDp = 400)
@Composable
private fun WithActionVariantPreview() {
    val textFieldState = rememberTextFieldState()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.WithAction(icon = R.drawable.question,
                text = null,
                onIconClick = {}
            ),
            uiState = VsTextInputFieldState.Idle,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.WithAction(icon = R.drawable.question,
                text = UiText.DynamicString("Text"),
                onIconClick = {}
            ),
            uiState = VsTextInputFieldState.Focus,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            footNote = UiText.DynamicString("some message"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.WithAction(
                text = UiText.DynamicString("Text"),
                icon = R.drawable.question,
                onIconClick = {},
                icon2 = R.drawable.camera,
            ),
            uiState = VsTextInputFieldState.Success,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            footNote = UiText.DynamicString("some message"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.WithAction(icon = R.drawable.question,
                text = UiText.DynamicString("Text"),
                onIconClick = {}
            ),
            uiState = VsTextInputFieldState.Error,
            onFocusChanged = {},
        )
    }
}

@Preview(widthDp = 400)
@Composable
private fun PasswordVariantPreview() {
    val textFieldState = rememberTextFieldState(initialText = "some password")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.Password(
                isVisible = true,
                onToggleVisibilityClick = {}
            ),
            uiState = VsTextInputFieldState.Idle,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.Password(
                isVisible = false,
                onToggleVisibilityClick = {}
            ),
            uiState = VsTextInputFieldState.Focus,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            footNote = UiText.DynamicString("some message"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.Password(
                isVisible = true,
                onToggleVisibilityClick = {}
            ),
            uiState = VsTextInputFieldState.Success,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            footNote = UiText.DynamicString("some message"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.Password(
                isVisible = false,
                onToggleVisibilityClick = {}
            ),
            uiState = VsTextInputFieldState.Error,
            onFocusChanged = {},
        )
    }
}


@Preview(widthDp = 400)
@Composable
private fun WrapContentVariantPreview() {
    val textFieldState = rememberTextFieldState(initialText = "text")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.WrapContent(),
            uiState = VsTextInputFieldState.Idle,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.WrapContent(),
            uiState = VsTextInputFieldState.Focus,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            footNote = UiText.DynamicString("some message"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.WrapContent(),
            uiState = VsTextInputFieldState.Success,
            onFocusChanged = {},
        )
        VsTextInputField(
            modifier = Modifier,
            label = UiText.DynamicString("Label"),
            hint = UiText.DynamicString("hint"),
            footNote = UiText.DynamicString("some message"),
            labelIcon = R.drawable.ic_question_mark,
            textFieldState = textFieldState,
            variant = VsTextInputFieldVariant.WrapContent(),
            uiState = VsTextInputFieldState.Error,
            onFocusChanged = {},
        )
    }
}


