package com.vultisig.wallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SelectionItem(
    title: String,
    isChecked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Theme.colors.backgrounds.secondary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { onCheckedChange?.invoke(!isChecked) }
                .padding(all = 12.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = Theme.colors.neutrals.n100,
                    style = Theme.montserrat.subtitle1,
                )
            }
            VsSwitch(
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Theme.colors.neutrals.n50,
                    checkedBorderColor = Theme.colors.backgrounds.teal,
                    checkedTrackColor = Theme.colors.backgrounds.teal,
                    uncheckedThumbColor = Theme.colors.neutrals.n50,
                    uncheckedBorderColor = Theme.colors.backgrounds.tertiary_2,
                    uncheckedTrackColor = Theme.colors.backgrounds.tertiary_2
                ),
                checked = isChecked,
                onCheckedChange = null,
            )
        }
    }
}

@Preview
@Composable
fun SelectionItemPreview() {
    SelectionItem(
        title = "Fast Vault",
    )
}