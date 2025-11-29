package com.vultisig.wallet.ui.components.v2.containers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ExpandedTopbarContainer(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Theme.v2.colors.backgrounds.primary,
    shineSpotColor: Color = Theme.v2.colors.primary.accent1,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width.toFloat()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        shineSpotColor,
                        backgroundColor
                    ),
                    radius = screenWidthPx,
                    center = Offset(
                        screenWidthPx / 2,
                        -screenWidthPx * 0.5f
                    )
                )
            )
            .padding(all = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}