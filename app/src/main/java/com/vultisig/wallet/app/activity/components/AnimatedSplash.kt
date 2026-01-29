package com.vultisig.wallet.app.activity.components


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AnimatedSplash(
    isLoading: Boolean,
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.splash_screen_logo)
    )

    val lottieState = animateLottieCompositionAsState(
        composition = composition,
    )

    LaunchedEffect(lottieState.isAtEnd, isLoading) {
        if (lottieState.progress == 1f && !isLoading) {
            onSplashComplete()
        }
    }

    LottieAnimation(
        composition = composition,
        progress = {
            lottieState.progress
        },
        modifier = modifier
            .fillMaxSize()
            .background(Theme.v2.colors.backgrounds.primary)
            .wrapContentSize(),
        contentScale = ContentScale.Fit
    )
}