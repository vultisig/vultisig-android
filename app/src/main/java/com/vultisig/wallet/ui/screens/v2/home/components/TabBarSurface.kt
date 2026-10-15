package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

/** The capsule behind the tabs: a sweep-gradient rim around a fixed-width track per tab. */
@Composable
internal fun TabBarSurface(
    tabCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val edge = Theme.v2.colors.backgrounds.secondary
    val accent = Theme.v2.colors.backgrounds.gradientMid
    val rim = Brush.sweepGradient(colors = listOf(edge, accent, accent, accent, edge, edge))
    Box(
        modifier =
            Modifier.clip(CircleShape)
                .background(brush = rim)
                .padding(1.dp)
    ) {
        Box(
            modifier =
                modifier
                    .height(64.dp)
                    .width(TabWidth * tabCount)
                    .clip(CircleShape)
                    .background(TrackColor)
                    .padding(all = 4.dp),
            content = content,
        )
    }
}

private val TabWidth = 92.dp

private val TrackColor = Color(0xFF0d2446)
