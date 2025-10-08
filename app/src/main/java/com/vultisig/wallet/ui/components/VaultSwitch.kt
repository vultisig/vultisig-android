package com.vultisig.wallet.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = Theme.colors.neutrals.n50,
        checkedTrackColor = Theme.colors.primary.accent4,
        uncheckedThumbColor = Theme.colors.neutrals.n50,
        uncheckedTrackColor = Theme.colors.neutral500,
        uncheckedBorderColor = Theme.colors.neutral500,
    ),
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    )
}