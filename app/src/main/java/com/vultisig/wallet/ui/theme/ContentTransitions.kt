package com.vultisig.wallet.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween


private typealias ContentTransitionFactory<T, R> = AnimatedContentTransitionScope<T>.() -> R

internal fun <T> slideInFromEndEnterTransition(): ContentTransitionFactory<T, EnterTransition> =
    {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = screenTransitionSpec(),
        )
    }

internal fun <T> slideInFromStartEnterTransition(): ContentTransitionFactory<T, EnterTransition> =
    {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = screenTransitionSpec(),
            initialOffset = { (it * SLIDE_OUT_TO).toInt() }
        )
    }

internal fun <T> slideInFromBottomEnterTransition(): ContentTransitionFactory<T, EnterTransition> =
    {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Up,
            animationSpec = screenTransitionSpec(),
        )
    }

internal fun <T> slideOutToStartExitTransition(): ContentTransitionFactory<T, ExitTransition> =
    {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = screenTransitionSpec(),
            targetOffset = { (it * SLIDE_OUT_TO).toInt() }
        )
    }

internal fun <T> slideOutToEndExitTransition(): ContentTransitionFactory<T, ExitTransition> =
    {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = screenTransitionSpec(),
        )
    }


internal const val SLIDE_OUT_TO = 0.1f

internal fun <T> screenTransitionSpec() =
    tween<T>(375)
