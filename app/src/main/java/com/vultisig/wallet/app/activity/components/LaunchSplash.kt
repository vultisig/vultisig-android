package com.vultisig.wallet.app.activity.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vultisig.wallet.ui.components.VultisigBrandMark
import com.vultisig.wallet.ui.theme.Theme
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private val MinimumDuration = 1.seconds

/**
 * Holds the app on the vault creation screen's mark and background until startup has loaded and
 * the splash has been up for [MinimumDuration], so a fast start does not flash it.
 */
@Composable
internal fun LaunchSplash(
    isLoading: Boolean,
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIsLoading by rememberUpdatedState(isLoading)
    val currentOnSplashComplete by rememberUpdatedState(onSplashComplete)

    LaunchedEffect(Unit) {
        delay(MinimumDuration)
        snapshotFlow { currentIsLoading }.first { !it }
        currentOnSplashComplete()
    }

    Box(
        modifier = modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.background),
        contentAlignment = Alignment.Center,
    ) {
        VultisigBrandMark()
    }
}
