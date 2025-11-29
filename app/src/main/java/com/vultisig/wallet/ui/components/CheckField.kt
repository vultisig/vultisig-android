package com.vultisig.wallet.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.library.UiCheckbox
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun CheckField(
    modifier: Modifier = Modifier,
    title: String,
    textStyle: TextStyle = Theme.menlo.body2,
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
        UiCheckbox(
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