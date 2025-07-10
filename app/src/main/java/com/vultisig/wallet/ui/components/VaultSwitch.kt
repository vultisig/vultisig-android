package com.vultisig.wallet.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.vultisig.wallet.ui.theme.Theme

@Deprecated("Use VsSwitch. The new design does not require scaling, and any changes will break compatibility")
@Composable
internal fun VaultSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: (@Composable () -> Unit)? = null,
){
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(0.6f),
        thumbContent = content,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource
    )
}

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