package com.vultisig.wallet.ui.components.v2.tab

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

internal class VsTabGroupScope {
    val tabs = mutableListOf<@Composable BoxScope.() -> Unit>()

    fun tab(content: @Composable BoxScope.() -> Unit) {
        tabs.add(content)
    }
}

@Composable
internal fun VsTabGroup(
    index: Int,
    content: VsTabGroupScope.() -> Unit,
) {
    val scope = VsTabGroupScope().apply(content)
    val tabs = scope.tabs

    val tabWidths = remember {
        mutableStateListOf<Dp>().apply {
            repeat(tabs.size) {
                add(0.dp)
            }
        }
    }
    val underLineWidth = tabWidths[index]
    val animateWidth by animateDpAsState(underLineWidth)
    val density = LocalDensity.current
    val itemsSpace = 16.dp

    Column(
        modifier = Modifier
    ) {
        Row(
            modifier = Modifier.padding(
                vertical = 6.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(
                space = itemsSpace,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            tabs.forEachIndexed { index, tab ->
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            tabWidths[index] = with(density) {
                                coordinates.size.width.toDp()
                            }
                        },
                    content = tab
                )
            }
        }

        val offsetAnimated = animateDpAsState(
            targetValue = if (index == 0) 0.dp
            else itemsSpace * (index) + tabWidths.take(index).reduce { acc, dp -> acc + dp })

        TabUnderLine(
            width = { animateWidth },
            offset = offsetAnimated::value
        )
    }
}

@Composable
internal fun VsTab(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    isEnabled: Boolean = true,
) {
    Text(
        text = label,
        color = if (isEnabled)
            Theme.v2.colors.text.primary
        else Theme.v2.colors.text.button.disabled,
        style = Theme.brockmann.body.s.medium,
        modifier = modifier
            .clickable(
                onClick = onClick,
                enabled = isEnabled,
            )
    )
}

@Composable
private fun TabUnderLine(
    width: () -> Dp,
    offset: () -> Dp,
) {
    HorizontalDivider(
        modifier = Modifier
            .layout { measurables, constraints ->
                val placeable = measurables.measure(
                    constraints = constraints.copy(
                        minWidth = width().roundToPx(),
                        maxWidth = width().roundToPx()
                    )
                )
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(
                        x = offset().roundToPx(),
                        y = 0
                    )
                }
            },
        color = Theme.v2.colors.primary.accent4,
        thickness = 1.5.dp
    )
}


@Preview
@Composable
internal fun TabGroupPreview() {
    VsTabGroup(
        index = 1
    ) {
        tab {
            VsTab(
                label = "Tab 1",
                onClick = {}
            )
        }
        tab {
            VsTab(
                label = "Tab 2",
                onClick = {}
            )
        }
        tab {
            VsTab(
                label = "Tab 3",
                onClick = {}
            )
        }
    }
}