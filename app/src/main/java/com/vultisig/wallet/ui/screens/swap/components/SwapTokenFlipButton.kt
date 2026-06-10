package com.vultisig.wallet.ui.screens.swap.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

/**
 * Circular flip button overlaid between the source and destination token inputs.
 *
 * Positions itself over the cutout shared by the two inputs (via [topCenter]/[bottomCenter]), spins
 * on tap, and shows a progress spinner while a quote loads or an error icon on failure.
 *
 * @param isLoading whether to show the progress spinner instead of the flip icon.
 * @param hasError whether to render the error styling and warning icon.
 * @param topCenter cutout circle bounds reported by the source input, used for positioning.
 * @param bottomCenter cutout circle bounds reported by the destination input, used for positioning.
 * @param space padding used inside the button so it lines up with the input cutout.
 * @param onFlip invoked when the button is tapped to flip the selected tokens.
 * @param onBoundsChanged reports the button's bottom-center, used to anchor the error hint.
 */
@Composable
internal fun SwapTokenFlipButton(
    isLoading: Boolean,
    hasError: Boolean,
    topCenter: Offset,
    bottomCenter: Offset,
    space: Dp,
    onFlip: () -> Unit,
    onBoundsChanged: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }

    // Trigger spin when this is incremented
    var spinTrigger by remember { mutableIntStateOf(0) }

    // Launch the animation every time trigger changes
    LaunchedEffect(spinTrigger) {
        rotation.snapTo(0f)
        rotation.animateTo(
            targetValue = 180f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        )
    }

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    this.translationY = -size.height / 2 + topCenter.y
                    this.translationX = (topCenter.x + bottomCenter.x).div(2) - (size.width) / 2
                }
                .size(40.dp)
                .background(
                    color =
                        if (hasError) Theme.v2.colors.alerts.error
                        else Theme.v2.colors.buttons.tertiary,
                    shape = CircleShape,
                )
                .padding(all = space)
                .onGloballyPositioned { onBoundsChanged(it.boundsInParent().bottomCenter) },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isLoading,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            },
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Theme.v2.colors.text.primary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter =
                        painterResource(
                            id =
                                if (!hasError) R.drawable.ic_arrow_bottom_top
                                else R.drawable.iconwarning
                        ),
                    contentDescription = null,
                    tint = Theme.v2.colors.text.primary,
                    modifier =
                        Modifier.clickable {
                                spinTrigger++
                                onFlip()
                            }
                            .size(24.dp)
                            .graphicsLayer { rotationZ = if (!hasError) rotation.value else 0f },
                )
            }
        }
    }
}
