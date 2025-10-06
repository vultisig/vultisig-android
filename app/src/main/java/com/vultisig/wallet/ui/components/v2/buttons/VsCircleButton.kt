package com.vultisig.wallet.ui.components.v2.buttons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.modifiers.shinedBottom
import com.vultisig.wallet.ui.theme.Theme


internal sealed class VsCircleButtonSize {
    object Small : VsCircleButtonSize()
    object Medium : VsCircleButtonSize()
    data class Custom(val size: Dp) : VsCircleButtonSize()
}


internal sealed class VsCircleButtonType {
    object Primary : VsCircleButtonType()
    object Secondary : VsCircleButtonType()
    object Tertiary : VsCircleButtonType()
    data class Custom(val color: Color) : VsCircleButtonType()
}

enum class DesignType {
    Shined, Solid,
}

@Composable
internal fun VsCircleButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    size: VsCircleButtonSize = VsCircleButtonSize.Medium,
    type: VsCircleButtonType = VsCircleButtonType.Primary,
    designType: DesignType = DesignType.Solid,
    hasBorder: Boolean = false,
) {
    val sizeInDp = when (size) {
        is VsCircleButtonSize.Custom -> size.size
        VsCircleButtonSize.Medium -> 64.dp
        VsCircleButtonSize.Small -> 44.dp
    }

    val backgroundColor = when (type) {
        VsCircleButtonType.Primary -> Theme.colors.primary.accent3
        VsCircleButtonType.Secondary -> Theme.colors.backgrounds.tertiary
        VsCircleButtonType.Tertiary -> Theme.colors.fills.tertiary.copy(alpha = 0.32f)
        is VsCircleButtonType.Custom -> type.color
    }

    Box(
        modifier = modifier
            .clickOnce(onClick = onClick)
            .size(sizeInDp)
            .clip(CircleShape)
            .background(
                color = backgroundColor
            )
            .then(
                if (designType == DesignType.Shined) {
                    Modifier
                        .shinedBottom()
                        .border(
                            width = 1.dp,
                            color = Theme.colors.neutrals.n100.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                } else {
                    if (hasBorder) {
                        Modifier.border(
                            width = 1.dp,
                            color = Theme.colors.neutrals.n100.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                    } else
                        Modifier
                }

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


@Composable
internal fun VsCircleButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    size: VsCircleButtonSize = VsCircleButtonSize.Medium,
    type: VsCircleButtonType = VsCircleButtonType.Primary,
    designType: DesignType = DesignType.Solid,
    hasBorder: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
    ) {
    val sizeInDp = when (size) {
        is VsCircleButtonSize.Custom -> size.size
        VsCircleButtonSize.Medium -> 64.dp
        VsCircleButtonSize.Small -> 44.dp
    }

    val backgroundColor = when (type) {
        VsCircleButtonType.Primary -> Theme.colors.primary.accent3
        VsCircleButtonType.Secondary -> Theme.colors.backgrounds.tertiary
        VsCircleButtonType.Tertiary -> Theme.colors.fills.tertiary.copy(alpha = 0.32f)
        is VsCircleButtonType.Custom -> type.color
    }

    Box(
        modifier = modifier
            .clickOnce(onClick = onClick)
            .size(sizeInDp)
            .clip(CircleShape)
            .background(
                color = backgroundColor
            )
            .then(
                if (designType == DesignType.Shined) {
                    Modifier
                        .shinedBottom()
                        .border(
                            width = 1.dp,
                            color = Theme.colors.neutrals.n100.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                } else {
                    if (hasBorder) {
                        Modifier.border(
                            width = 1.dp,
                            color = Theme.colors.neutrals.n100.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                    } else
                        Modifier
                }

            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
internal fun VsCircleButton(
    modifier : Modifier = Modifier,
    @DrawableRes drawableResId: Int,
    tint: Color = Theme.colors.neutrals.n100,
    onClick: () -> Unit,
    size: VsCircleButtonSize = VsCircleButtonSize.Small,
    type: VsCircleButtonType = VsCircleButtonType.Primary,
    designType: DesignType = DesignType.Shined,
    iconSize: Dp = 48.dp,
    hasBorder: Boolean = false,
){
    VsCircleButton(
        modifier = modifier,
        onClick = onClick,
        size = size,
        designType = designType,
        type = type,
        hasBorder = hasBorder,
        content = {
            UiIcon(
                drawableResId = drawableResId,
                tint = tint,
                size = iconSize
            )
        }
    )
}

@Preview
@Composable
private fun PreviewVsCircleButton() {
    VsCircleButton(
        onClick = {},
        size = VsCircleButtonSize.Medium,
        icon = R.drawable.camera,
        designType = DesignType.Shined
    )
}

@Preview
@Composable
private fun PreviewVsCircleButton2() {
    VsCircleButton(
        onClick = {},
        size = VsCircleButtonSize.Medium,
        icon = R.drawable.camera,
        designType = DesignType.Solid,
    )
}

@Preview
@Composable
private fun PreviewVsCircleButton3() {
    VsCircleButton(
        onClick = {},
        size = VsCircleButtonSize.Medium,
        icon = R.drawable.camera,
        type = VsCircleButtonType.Secondary,
        designType = DesignType.Shined
    )
}

@Preview
@Composable
private fun PreviewVsCircleButton4() {
    VsCircleButton(
        onClick = {},
        size = VsCircleButtonSize.Small,
        icon = R.drawable.camera,
        type = VsCircleButtonType.Tertiary,
        designType = DesignType.Shined
    )
}


@Preview
@Composable
private fun PreviewVsCircleButton5() {
    VsCircleButton(
        onClick = {},
        size = VsCircleButtonSize.Small,
        designType = DesignType.Shined,
        drawableResId = R.drawable.big_tick,
    )
}
