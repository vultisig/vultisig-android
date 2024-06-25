package com.vultisig.wallet.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

@Composable
internal fun VaultSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
){
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(0.6f),
        thumbContent = thumbContent,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource
    )
}