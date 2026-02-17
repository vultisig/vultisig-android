package com.vultisig.wallet.ui.screens.v3.onboarding.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultCountSelector(
    count: Int,
    modifier: Modifier = Modifier
) {
    val thumbPosition = remember(count) {
        when (count) {
            1 -> -1f
            2 -> -0.5f
            3 -> 0f
            4 -> 1f
            else -> error("not possible")
        }
    }

    val thumbPositionAnimated by animateFloatAsState(
        targetValue = thumbPosition,
        label = "thumbPosition"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Box(modifier = modifier) {
            TrackBackground()

            AnimatedThumb(
                position = thumbPositionAnimated
            )
        }

        UiSpacer(
            size = 6.dp
        )


        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp)
                .clip(CircleShape)
                .background(Theme.v2.colors.border.light)
        )

        UiSpacer(
            size = 4.dp,
        )


        Text(
            text = stringResource(R.string.vault_count_recommended),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.neutrals.n50
        )
    }

}

@Composable
private fun BoxScope.TrackBackground() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.Center)
            .height(12.dp)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(218, 255, 246),
                        Color(19, 200, 157),
                        Color(92, 167, 255),
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = Theme.v2.colors.neutrals.n50,
                    shape = CircleShape
                ),
        )
    }
}

@Composable
private fun BoxScope.AnimatedThumb(
    position: Float
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = Theme.v2.colors.neutrals.n50,
                shape = CircleShape
            )
            .align(
                BiasAlignment(
                    horizontalBias = position,
                    verticalBias = 0f
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        UiIcon(
            drawableResId = R.drawable.icon_shield_solid,
            tint = Theme.v2.colors.text.inverse,
            size = 20.dp
        )
    }
}


@Composable
@Preview
private fun VaultCountSelectorPreview() {
    VaultCountSelector(
        count = 2,
    )
}