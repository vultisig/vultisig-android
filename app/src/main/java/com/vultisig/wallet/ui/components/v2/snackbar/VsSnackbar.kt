package com.vultisig.wallet.ui.components.v2.snackbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.loading.V2ProgressiveLoading
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun VsSnackBar(
    modifier: Modifier = Modifier,
    snackbarState: VSSnackbarState,
) {

    val durationMillis = 300
    val snackbarState by snackbarState.progressState.collectAsState()
    AnimatedVisibility(
        visible = snackbarState.isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis, easing = EaseOutCubic)
        ) + fadeIn(
            animationSpec = tween(durationMillis)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis, easing = EaseInCubic)
        ) + fadeOut(
            animationSpec = tween(durationMillis)
        ),
        modifier = modifier
    ) {
        V2Container(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            type = ContainerType.TERTIARY,
            borderType = ContainerBorderType.Bordered(
                color = Theme.colors.borders.normal,
            ),
            cornerType = CornerType.RoundedCornerShape(
                size = 24.dp
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                V2ProgressiveLoading(
                    progress = snackbarState.progress,
                )

                UiSpacer(
                    size = 8.dp
                )
                Text(
                    text = snackbarState.message,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.colors.neutral0
                )
            }
        }
    }


}