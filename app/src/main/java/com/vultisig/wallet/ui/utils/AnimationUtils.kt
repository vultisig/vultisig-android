package com.vultisig.wallet.ui.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp

internal fun Modifier.startScreenAnimations(
    delay: Int,
    duration: Int = 200,
) = composed {

    val start = 0f
    val end = 1.0f
    val yStartOffset = 100
    val yEndOffset = 0
    val startScale = 0.5f
    val endScale = 1f

    val animationValue = remember {
        Animatable(start)
    }

    LaunchedEffect(Unit) {
        animationValue.animateTo(
            targetValue = end,
            animationSpec = tween(
                durationMillis = duration,
                delayMillis = delay
            )
        )
    }

    this
        .scale(lerp(startScale, endScale, animationValue.value))
        .offset {
            IntOffset(0, lerp(yStartOffset, yEndOffset, animationValue.value))
        }
        .alpha(animationValue.value)
}