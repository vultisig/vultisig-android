package com.vultisig.wallet.ui.components.v2.containers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ExpandedTopbarContainer(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Theme.v2.colors.backgrounds.primary,
    shineSpotColor: Color = Theme.v2.colors.primary.accent1,
    shineSpotCenterXRatio: Float = 0.5f,
    shineSpotCenterYRatio: Float = -0.5f,
    shineSpotRadiusRatio: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width.toFloat()
    val backgroundBrush =
        remember(
            shineSpotColor,
            backgroundColor,
            screenWidthPx,
            shineSpotCenterXRatio,
            shineSpotCenterYRatio,
            shineSpotRadiusRatio,
        ) {
            Brush.radialGradient(
                colors = listOf(shineSpotColor, backgroundColor),
                radius = screenWidthPx * shineSpotRadiusRatio,
                center =
                    Offset(
                        screenWidthPx * shineSpotCenterXRatio,
                        screenWidthPx * shineSpotCenterYRatio,
                    ),
            )
        }

    Column(
        modifier = modifier.fillMaxWidth().background(brush = backgroundBrush).padding(all = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Preview
@Composable
private fun ExpandedTopbarContainerPreview() {
    ExpandedTopbarContainer(
        shineSpotColor = Theme.v2.colors.primary.accent2.copy(alpha = 0.35f),
        shineSpotCenterXRatio = 0.92f,
        shineSpotCenterYRatio = -0.15f,
        shineSpotRadiusRatio = 0.45f,
    ) {
        Spacer(modifier = Modifier.height(112.dp))
    }
}
