package com.vultisig.wallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.cursorBrush
import java.math.BigDecimal

@Composable
internal fun TokenAmountInput(
    primaryFieldState: TextFieldState,
    primaryLabel: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
    inputModifier: Modifier = Modifier,
    maxBalance: BigDecimal? = null,
    focusRequester: FocusRequester? = null,
    onLostFocus: (() -> Unit)? = null,
    onKeyboardAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlowRow(horizontalArrangement = Arrangement.Center) {
            BasicTextField(
                state = primaryFieldState,
                inputTransformation =
                    InputTransformation {
                        val raw = asCharSequence().toString()
                        if (raw.isEmpty()) return@InputTransformation
                        val normalized = raw.replace(',', '.')
                        val dotCount = normalized.count { it == '.' }
                        val isValid = dotCount <= 1 && normalized.all { it.isDigit() || it == '.' }
                        if (!isValid) {
                            revertAllChanges()
                            return@InputTransformation
                        }
                        if (maxBalance != null) {
                            val entered = normalized.toBigDecimalOrNull()
                            if (entered != null && entered > maxBalance) {
                                revertAllChanges()
                            }
                        }
                    },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                textStyle =
                    Theme.brockmann.headings.largeTitle.copy(
                        color = Theme.v2.colors.text.primary,
                        textAlign = TextAlign.Center,
                    ),
                cursorBrush = Theme.cursorBrush,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction =
                            if (onKeyboardAction != null) ImeAction.Send else ImeAction.Default,
                    ),
                onKeyboardAction = onKeyboardAction?.let { action -> { action() } },
                modifier =
                    Modifier.width(IntrinsicSize.Min)
                        .then(
                            if (focusRequester != null) Modifier.focusRequester(focusRequester)
                            else Modifier
                        )
                        .then(
                            if (onLostFocus != null) {
                                var wasFocused by remember { mutableStateOf(false) }
                                Modifier.onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        wasFocused = true
                                    } else if (wasFocused) {
                                        onLostFocus()
                                    }
                                }
                            } else Modifier
                        )
                        .then(inputModifier),
                decorator = { textField ->
                    if (primaryFieldState.text.isEmpty()) {
                        Text(
                            text = "0",
                            color = Theme.v2.colors.text.secondary,
                            style = Theme.brockmann.headings.largeTitle,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.wrapContentWidth(),
                        )
                    }
                    textField()
                },
            )

            Text(
                text = " $primaryLabel",
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.headings.largeTitle,
                textAlign = TextAlign.Center,
            )
        }

        if (secondaryText.isNotEmpty()) {
            Text(
                text = secondaryText,
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
