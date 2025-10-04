package com.vultisig.wallet.ui.components.v2.pager.indicator

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme


internal data class PagerIndicatorUiModel(
    val selectedColor: Color,
    val defaultColor: Color,
    val defaultRadius: Dp,
    val selectedLength: Dp,
    val space: Dp,
    val animationDurationInMillis: Int,
)

@Composable
internal fun rememberPagerIndicatorUiModel(
    selectedColor: Color = Theme.colors.text.primary,
    defaultColor: Color = Theme.colors.backgrounds.tertiary,
    defaultRadius: Dp = 4.dp,
    selectedLength: Dp = 12.dp,
    space: Dp = 4.dp,
    animationDurationInMillis: Int = 300,
) = PagerIndicatorUiModel(
    selectedColor = selectedColor,
    defaultColor = defaultColor,
    defaultRadius = defaultRadius,
    selectedLength = selectedLength,
    space = space,
    animationDurationInMillis = animationDurationInMillis
)
