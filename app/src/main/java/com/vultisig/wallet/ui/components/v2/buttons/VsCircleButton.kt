package com.vultisig.wallet.ui.components.v2.buttons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.modifiers.partialBorder
import com.vultisig.wallet.ui.theme.Theme


internal sealed class VsCircleButtonSize {
    object Small : VsCircleButtonSize()
    object Medium : VsCircleButtonSize()
    data class Custom(val size: Dp) : VsCircleButtonSize()
}


internal sealed class VsCircleButtonType {
    object Primary : VsCircleButtonType()
    object Secondary : VsCircleButtonType()
}

@Composable
internal fun VsCircleButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    size: VsCircleButtonSize = VsCircleButtonSize.Medium,
    type: VsCircleButtonType = VsCircleButtonType.Primary
) {
    val sizeInDp = when (size) {
        is VsCircleButtonSize.Custom -> size.size
        VsCircleButtonSize.Medium -> 64.dp
        VsCircleButtonSize.Small -> 44.dp
    }

    val backgroundColor = when (type) {
        VsCircleButtonType.Primary -> Theme.colors.primary.accent3
        VsCircleButtonType.Secondary -> Theme.colors.backgrounds.tertiary
    }

    Box(
        modifier = Modifier
            .clickOnce(onClick = onClick)
            .size(sizeInDp)
            .clip(CircleShape)
            .background(
                color = backgroundColor
            )
            .partialBorder(
                width = 0.5.dp,
                color = Theme.colors.neutrals.n100,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center
    ) {
        UiIcon(
            drawableResId = icon,
            tint = Theme.colors.neutrals.n100,
            size = 20.dp
        )
    }
}

@Preview
@Composable
private fun PreviewVsCircleButton() {
    VsCircleButton(
        onClick = {},
        size = VsCircleButtonSize.Medium,
        icon = R.drawable.camera
    )
}