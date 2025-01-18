package com.vultisig.wallet.ui.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

internal fun Modifier.startScreenAnimations(
    delay: Long,
    label: String,
    duration: Int = 1000,
    isAnimationRunning: Boolean
) = composed {

    val start = 0.5f
    val startDeg = -90f
    val end = 1.0f
    val endDeg = 0f
    val yStartOffset = 400

    val animationValue = animateFloatAsState(
        targetValue = if (isAnimationRunning) end else start,
        label = label,
        animationSpec = tween(
            durationMillis = duration,
            delayMillis = delay.toInt()
        )
    )
    this
        .graphicsLayer {
            this.transformOrigin = TransformOrigin(0.5f, 1f)
            this.rotationX =
                ((endDeg - startDeg) / end - start).times(animationValue.value) + startDeg
        }
        .offset {
            IntOffset(0, (yStartOffset * (1 - animationValue.value)).toInt())
        }
        .alpha((start - animationValue.value).div(start - end))

}