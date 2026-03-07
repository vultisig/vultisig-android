package com.vultisig.wallet.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors =
        SwitchDefaults.colors(
            checkedThumbColor = Theme.v2.colors.neutrals.n50,
            checkedTrackColor = Theme.v2.colors.primary.accent4,
            uncheckedThumbColor = Theme.v2.colors.neutrals.n50,
            uncheckedTrackColor = Theme.v2.colors.neutrals.n500,
            uncheckedBorderColor = Theme.v2.colors.neutrals.n500,
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
@Composable
internal fun VsSwitchV3(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor =
        if (checked) Theme.v2.colors.primary.accent4 else Theme.v2.colors.text.tertiary

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        label = "VsSwitchV3.thumbOffset",
    )

    Box(
        modifier =
            modifier
                .width(51.dp)
                .height(31.dp)
                .clip(RoundedCornerShape(50))
                .background(trackColor)
                .then(
                    if (enabled && onCheckedChange != null)
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onCheckedChange(!checked) }
                    else Modifier
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbOffset)
                    .align(Alignment.CenterStart)
                    .padding(vertical = 2.dp)
                    .size(27.dp)
                    .clip(CircleShape)
                    .background(Theme.v2.colors.neutrals.n50),
        )
    }
}
