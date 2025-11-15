package com.vultisig.wallet.ui.components.v2.fastselection.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun PopupPickerItem(
    distanceFromCenter: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale = when (distanceFromCenter) {
        0 -> 1f
        1 -> 0.95f
        2 -> 0.90f
        else -> 0.85f
    }

    val alpha = when (distanceFromCenter) {
        0 -> 1f
        1 -> 0.7f
        2 -> 0.4f
        else -> 0.2f
    }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        animationSpec = tween(durationMillis = 200),
        label = "alpha"
    )

    Surface(
        modifier = modifier
            .padding(vertical = 8.dp)
            .scale(animatedScale)
            .alpha(animatedAlpha),
        color = Color.Transparent
    ) {
        content()
    }
}