package com.vultisig.wallet.ui.screens.v3.onboarding.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v3.AnimatedItem
import com.vultisig.wallet.ui.components.v3.MinusSign
import com.vultisig.wallet.ui.components.v3.PlusSign
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultCountSelector(
    count: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
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

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            ChangeCountButton(
                enabled = count > 1,
                onClick = onDecrease,
                isIncrease = false,
            )

            AnimatedItem(
                current = count,
                items = (1..4).toList(),
                color = Theme.v2.colors.neutrals.n50,
                style = Theme.brockmann.headings.headline,
            ) { it: Int ->
                if (it == 4) "+$it"
                else "$it"
            }

            ChangeCountButton(
                enabled = count < 4,
                onClick = onIncrease,
                isIncrease = true,
            )
        }

        UiSpacer(
            size = 22.dp
        )

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
private fun ChangeCountButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    isIncrease: Boolean,
) {
    Box(
        modifier = modifier
            .alpha(animateFloatAsState(if (enabled) 1f else 0.5f).value)
            .size(
                width = 64.dp,
                height = 46.dp,
            )
            .clip(
                shape = CircleShape
            )
            .background(
                color =
                    animateColorAsState( targetValue =
                        if (enabled)
                            Theme.v2.colors.backgrounds.surface2
                        else
                            Theme.v2.colors.buttons.ctaDisabled
                    ).value
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        content = {
            if (isIncrease) {
                PlusSign(
                    modifier = Modifier.size(
                        15.dp
                    ),
                )
            } else {
                MinusSign(
                    modifier = Modifier.size(
                        15.dp
                    ),
                )
            }
        },
        contentAlignment = Alignment.Center,
    )
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
    var count by remember {
        mutableIntStateOf(1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Theme.v2.colors.backgrounds.background
            )
            .padding(
                16.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        VaultCountSelector(
            count = count,
            onIncrease = {
                count++
            },
            onDecrease = {
                count--
            }
        )
    }
}