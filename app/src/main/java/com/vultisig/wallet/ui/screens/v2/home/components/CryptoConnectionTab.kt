package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.theme.Theme

/** One tab of [CryptoConnectionSelect]; its icon bounces when it becomes highlighted. */
@Composable
internal fun CryptoConnectionTab(
    type: CryptoConnectionType,
    isHighlighted: Boolean,
    isBounceEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val iconScale = remember { Animatable(1f) }
    var wasHighlighted by remember { mutableStateOf(isHighlighted) }

    LaunchedEffect(isHighlighted) {
        val isNewlyHighlighted = isHighlighted && !wasHighlighted
        wasHighlighted = isHighlighted
        if (!isNewlyHighlighted || !isBounceEnabled) return@LaunchedEffect
        iconScale.animateTo(ICON_BOUNCE_SCALE, tween(ICON_BOUNCE_OUT_MILLIS))
        iconScale.animateTo(
            1f,
            spring(dampingRatio = ICON_BOUNCE_DAMPING, stiffness = ICON_BOUNCE_STIFFNESS),
        )
    }

    Column(
        modifier =
            modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val contentColor =
            if (isHighlighted) Theme.v2.colors.text.primary else Theme.v2.colors.text.tertiary
        val contentColorAnimated by animateColorAsState(contentColor)
        UiIcon(
            drawableResId = type.icon,
            size = 24.dp,
            tint = contentColorAnimated,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                },
        )
        Text(
            text = type.label(),
            style = Theme.brockmann.supplementary.captionSmall,
            color = contentColorAnimated,
        )
    }
}

@Composable
private fun CryptoConnectionType.label(): String =
    when (this) {
        CryptoConnectionType.Wallet -> stringResource(R.string.wallet)
        CryptoConnectionType.Defi -> stringResource(R.string.defi)
    }

@get:DrawableRes
private val CryptoConnectionType.icon: Int
    get() =
        when (this) {
            CryptoConnectionType.Wallet -> R.drawable.wallet_filled
            CryptoConnectionType.Defi -> R.drawable.nodes
        }

private const val ICON_BOUNCE_SCALE = 1.22f

// iOS keyframes: out, then `.bouncy` back; damping = 1 − bounce, stiffness = (2π / 0.5 s)².
private const val ICON_BOUNCE_OUT_MILLIS = 140
private const val ICON_BOUNCE_DAMPING = 0.45f
private const val ICON_BOUNCE_STIFFNESS = 158f
