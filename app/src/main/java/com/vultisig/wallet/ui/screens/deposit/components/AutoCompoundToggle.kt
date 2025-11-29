package com.vultisig.wallet.ui.screens.deposit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.VsSwitch
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AutoCompoundToggle(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: ((Boolean) -> Unit),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(all = 12.dp)
            .toggleable(
                value = isChecked,
                onValueChange = onCheckedChange
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = Theme.v2.colors.neutrals.n100,
                style = Theme.menlo.body1
            )
            Text(
                text = subtitle,
                color = Theme.v2.colors.neutrals.n100,
                style = Theme.menlo.body3,
            )
        }


        VsSwitch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
        )

    }
}