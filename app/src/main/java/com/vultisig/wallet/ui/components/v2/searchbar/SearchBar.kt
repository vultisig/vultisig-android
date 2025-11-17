package com.vultisig.wallet.ui.components.v2.searchbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.PasteIcon
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SearchBar(
    modifier: Modifier = Modifier,
    state: TextFieldState,
    onCancelClick: () -> Unit,
    isInitiallyFocused: Boolean,
    isPasteEnabled: Boolean = false,
    onSetSearchText: (String) -> Unit = {},
) {
    var isFocusedState by remember { mutableStateOf(isInitiallyFocused) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember {
        FocusRequester()
    }

    LaunchedEffect(key1 = Unit) {
        if (isInitiallyFocused)
            focusRequester.requestFocus()
    }

    LaunchedEffect(key1 = isFocusedState) {
        if (isFocusedState.not()) {
            focusManager.clearFocus()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        V2Container(
            type = ContainerType.SECONDARY,
            borderType = if (isFocusedState)
                ContainerBorderType.Bordered(
                    color = Theme.colors.borders.normal,
                )
            else ContainerBorderType.Borderless,
            modifier = Modifier
                .weight(1f),
            cornerType = CornerType.Circular,
        ) {

            BasicTextField(
                state = state,
                cursorBrush = Brush.linearGradient(
                    colors = listOf(
                        Theme.colors.primary.accent4,
                        Theme.colors.primary.accent4,
                    )
                ),
                modifier = Modifier
                    .padding(
                        all = 12.dp,
                    )
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocusedState = it.isFocused
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default
                ),
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = Theme.brockmann.supplementary.footnote.copy(
                    color = Theme.colors.text.primary
                ),
                decorator = { input ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UiIcon(
                            drawableResId = R.drawable.ic_search,
                            size = 16.dp,
                            tint = Theme.colors.text.primary,
                        )
                        UiSpacer(
                            8.dp,
                        )
                        if (state.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_bar_search),
                                color = Theme.colors.text.extraLight,
                                style = Theme.brockmann.supplementary.footnote,
                            )
                            UiSpacer(
                                weight = 1f
                            )
                            if (isPasteEnabled) {
                                PasteIcon(onPaste = onSetSearchText)
                            }
                        } else {
                            input()
                            UiSpacer(
                                weight = 1f
                            )
                            UiIcon(
                                drawableResId = R.drawable.close_circle,
                                size = 18.dp,
                                tint = Theme.colors.neutrals.n300,
                                onClick = {
                                    state.clearText()
                                }
                            )
                        }
                    }
                }
            )
        }


        AnimatedContent(targetState = isFocusedState) { focused ->
            if (focused)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UiSpacer(
                        size = 8.dp
                    )
                    Text(
                        text = stringResource(R.string.search_bar_cancel),
                        color = Theme.colors.text.primary,
                        style = Theme.brockmann.body.s.medium,
                        modifier = Modifier
                            .clickable(
                                onClick = {
                                    isFocusedState = false
                                    state.clearText()
                                    onCancelClick()
                                }
                            )
                    )
                }
        }

    }
}

@Preview
@Composable
private fun PreviewSearchBar() {
    SearchBar(
        state = rememberTextFieldState(),
        onCancelClick = {},
        isInitiallyFocused = true
    )
}