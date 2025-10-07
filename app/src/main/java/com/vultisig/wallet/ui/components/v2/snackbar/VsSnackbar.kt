package com.vultisig.wallet.ui.components.v2.snackbar

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R.drawable
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
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

                ProgressiveCircle(
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

@Composable
private fun ProgressiveCircle(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Dp = 2.dp,
    backgroundColor: Color = Theme.colors.borders.normal,
    progressColor: Color = Theme.colors.alerts.success,
    iconColor: Color = Theme.colors.alerts.success,
    @DrawableRes icon: Int = drawable.ic_check,
) {
    val bounceScale by animateFloatAsState(
        targetValue = if (progress > 0f) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(bounceScale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = this.center
            val radius = (size.toPx() - strokeWidth.toPx()) / 2


            drawCircle(
                color = backgroundColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth.toPx())
            )


            if (progress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(
                        width = strokeWidth.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
        }

        UiIcon(
            drawableResId = icon,
            tint = iconColor,
            size = size * 0.8f
        )

    }
}
