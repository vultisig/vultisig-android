package com.vultisig.wallet.ui.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

internal fun Modifier.startScreenAnimations(
    delay: Long,
    label: String,
    duration: Int = 200,
    isAnimationRunning: Boolean
) = composed {

    val start = 0f
    val end = 1.0f
    val yStartOffset = 100

    val animationValue by  animateFloatAsState(
        targetValue = if (isAnimationRunning) end else start,
        label = label,
        animationSpec = tween(
            durationMillis = duration,
            delayMillis = delay.toInt()
        )
    )
    graphicsLayer {
        transformOrigin = TransformOrigin(
            0.5f, 1f
        ) //animate from bottom
        scaleY = animationValue
        scaleX = animationValue
    }
        .offset {
            IntOffset(0, yStartOffset * (1 - animationValue).toInt())
        }
        .alpha(animationValue)
}