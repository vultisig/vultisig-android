package com.vultisig.wallet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.library.form.VsUiCheckbox
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsCheckField(
    modifier: Modifier = Modifier,
    title: String,
    textStyle: TextStyle = Theme.brockmann.body.s.medium,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 4.dp,
                vertical = 8.dp,
            )
            .toggleable(
                value = isChecked,
                onValueChange = { checked ->
                    onCheckedChange(checked)
                }
            )
    ) {
        VsUiCheckbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )

        UiSpacer(size = 8.dp)

        Text(
            text = title,
            color = Theme.v2.colors.neutrals.n100,
            style = textStyle,
        )
    }
}

@Preview
@Composable
private fun VsCheckFieldPreview() {
    Column {
        VsCheckField(
            title = "Checked",
            isChecked = true,
            onCheckedChange = {}
        )

        VsCheckField(
            title = "Not checked",
            isChecked = false,
            onCheckedChange = {}
        )
    }
}