package com.vultisig.wallet.ui.components.v2.scaffold

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.components.v2.snackbar.VsSnackBar


@Composable
internal fun ScaffoldWithExpandableTopBar(
    topBarCollapsedHeight: Dp = 64.dp,
    topBarExpandedHeight: Dp = 300.dp,
    isTopbarExpanded: Boolean,
    snackbarState: VSSnackbarState,
    topBarExpandedContent: @Composable BoxScope.() -> Unit,
    topBarCollapsedContent: @Composable BoxScope.() -> Unit,
    bottomBarContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val animatedCollapsedFraction by animateFloatAsState(
        targetValue = if (isTopbarExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = 500,
            easing = EaseOutCubic
        ),
    )

    val currentHeight = lerp(
        start = topBarExpandedHeight,
        stop = topBarCollapsedHeight,
        fraction = animatedCollapsedFraction
    )


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            VsSnackBar(snackbarState = snackbarState)
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // main content with top padding for the app bar
            Box(
                modifier = Modifier
                    .padding(
                        top = currentHeight,
                    ),
                content = content
            )

            // Floating top bar that changes height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentHeight)
                    .align(Alignment.TopCenter)
            ) {
                // Expanded topbar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(1f - if (isTopbarExpanded) 1f else 0f),
                    content = topBarExpandedContent
                )


                // Collapsed topbar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isTopbarExpanded) 1f else 0f),
                    content = topBarCollapsedContent
                )
            }



            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                content = bottomBarContent,
            )
        }
    }

}
