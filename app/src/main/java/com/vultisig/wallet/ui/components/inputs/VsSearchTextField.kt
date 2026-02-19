package com.vultisig.wallet.ui.components.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.library.form.BasicFormTextField
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsSearchTextField(
    fieldState: TextFieldState,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(
                all = 16.dp,
            )
            .background(
                color = Theme.v2.colors.fills.primary,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(
                all = 8.dp,
            )
    ) {
        UiIcon(
            drawableResId = R.drawable.ic_search,
            size = 22.dp,
            tint = Theme.v2.colors.text.tertiary,
        )

        BasicFormTextField(
            textFieldState = fieldState,
            textStyle = Theme.brockmann.body.m.medium,
            hintColor = Theme.v2.colors.text.tertiary,
            hint = stringResource(R.string.token_selection_search_hint),
            keyboardType = KeyboardType.Text,
            onLostFocus = { /* no-op */ },
            modifier = Modifier
                .fillMaxWidth(),
        )
    }
}