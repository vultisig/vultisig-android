package com.vultisig.wallet.ui.components.v3

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

internal fun slideDown(): ContentTransform =
    (slideInVertically { height -> -height } + fadeIn()).togetherWith(
        slideOutVertically { height -> height } + fadeOut())


internal fun slideUp(): ContentTransform =
    (slideInVertically { height -> height } + fadeIn()).togetherWith(
        slideOutVertically { height -> -height } + fadeOut())


internal fun slideLeft(): ContentTransform =
    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
        slideOutHorizontally { width -> -width } + fadeOut())


internal fun slideRight(): ContentTransform =
    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
        slideOutHorizontally { width -> width } + fadeOut())

