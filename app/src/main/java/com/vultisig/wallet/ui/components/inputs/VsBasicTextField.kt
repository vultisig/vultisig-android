package com.vultisig.wallet.ui.components.inputs

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.cursorBrush

@Composable
internal fun VsBasicTextField(
    textFieldState: TextFieldState,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Theme.v2.colors.text.light,
    textAlign: TextAlign = TextAlign.Start,
    hint: String? = null,
    hintColor: Color = Theme.v2.colors.text.extraLight,
    hintStyle: TextStyle = style,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    BasicTextField(
        state = textFieldState,
        textStyle = style
            .copy(
                color = color,
                textAlign = textAlign,
            ),
        cursorBrush = Theme.cursorBrush,
        lineLimits = lineLimits,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        decorator = { textField ->
            if (textFieldState.text.isEmpty() && hint != null) {
                Text(
                    text = hint,
                    color = hintColor,
                    style = hintStyle,
                    textAlign = textAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            textField()
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )
}