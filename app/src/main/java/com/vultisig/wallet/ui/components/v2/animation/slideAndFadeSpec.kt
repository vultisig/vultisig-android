package com.vultisig.wallet.ui.components.v2.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

private const val STEP_SLIDE_DURATION_MS = 300
private const val STEP_FADE_DURATION_MS = 200

internal fun slideAndFadeSpec(): AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = {
    if (targetState) {
        (slideInHorizontally(
                animationSpec = tween(),
                initialOffsetX = { fullWidth -> -fullWidth },
            ) + fadeIn(animationSpec = tween()))
            .togetherWith(
                exit =
                    slideOutHorizontally(
                        animationSpec = tween(),
                        targetOffsetX = { fullWidth -> fullWidth },
                    ) + fadeOut(animationSpec = tween())
            )
    } else {
        (slideInHorizontally(animationSpec = tween(), initialOffsetX = { fullWidth -> fullWidth }) +
                fadeIn(animationSpec = tween()))
            .togetherWith(
                exit =
                    slideOutHorizontally(
                        animationSpec = tween(),
                        targetOffsetX = { fullWidth -> -fullWidth },
                    ) + fadeOut(animationSpec = tween())
            )
    }
}

/**
 * Slide plus fade between ordered steps: moving to a higher [index] brings the new step in from the
 * trailing edge, a lower one from the leading edge. With [isReduceMotion] it only crossfades.
 */
internal fun <T> stepSlideAndFadeSpec(
    index: (T) -> Int,
    isReduceMotion: Boolean,
): AnimatedContentTransitionScope<T>.() -> ContentTransform = {
    if (isReduceMotion) {
        fadeIn(tween(STEP_FADE_DURATION_MS)) togetherWith fadeOut(tween(STEP_FADE_DURATION_MS))
    } else {
        val direction = if (index(targetState) > index(initialState)) 1 else -1
        val slide = tween<IntOffset>(STEP_SLIDE_DURATION_MS, easing = EaseInOut)
        val fade = tween<Float>(STEP_SLIDE_DURATION_MS, easing = EaseInOut)
        (slideInHorizontally(slide) { it * direction } + fadeIn(fade)) togetherWith
            (slideOutHorizontally(slide) { -it * direction } + fadeOut(fade))
    }
}
