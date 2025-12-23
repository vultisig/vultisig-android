package com.vultisig.wallet.ui.components.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

internal fun slideAndFadeSpec(): AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = {
    if (targetState) {
        (slideInHorizontally(
            animationSpec = tween(),
            initialOffsetX = { fullWidth -> -fullWidth })
                + fadeIn(animationSpec = tween()))
            .togetherWith(
                exit = slideOutHorizontally(
                    animationSpec = tween(),
                    targetOffsetX = { fullWidth -> fullWidth }) + fadeOut(animationSpec = tween())
            )
    } else {
        (slideInHorizontally(
            animationSpec = tween(),
            initialOffsetX = { fullWidth -> fullWidth }
        ) + fadeIn(animationSpec = tween()))
            .togetherWith(
                exit = slideOutHorizontally(
                    animationSpec = tween(),
                    targetOffsetX = { fullWidth -> -fullWidth }
                ) + fadeOut(animationSpec = tween()))
    }

}