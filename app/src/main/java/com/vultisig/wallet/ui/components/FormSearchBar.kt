package com.vultisig.wallet.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.library.form.BasicFormTextField
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun FormSearchBar(
    textFieldState: TextFieldState,
    modifier: Modifier = Modifier,
) {
    FormCard(
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(
                    horizontal = 12.dp,
                )
        ) {
            UiIcon(
                drawableResId = R.drawable.ic_search,
                size = 24.dp,
                tint = Theme.v2.colors.neutrals.n500
            )

            BasicFormTextField(
                textFieldState = textFieldState,
                hint = stringResource(R.string.token_selection_search_hint),
                keyboardType = KeyboardType.Text,
                onLostFocus = {
                    // todo no validation needed
                },
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun FormSearchBarPreview() {
    FormSearchBar(
        textFieldState = TextFieldState()
    )
}