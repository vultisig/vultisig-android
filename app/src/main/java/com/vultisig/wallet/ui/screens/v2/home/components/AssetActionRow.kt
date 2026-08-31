package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

internal data class AssetActionItem(
    val action: AssetAction,
    val isSelected: Boolean = action == AssetAction.SWAP,
    val onClick: () -> Unit,
)

/** Tightest gap the row falls back to before it starts taking width off the buttons. */
internal val AssetActionRowMinSpacing = 8.dp

/** Gap the row uses whenever the buttons leave that much room. */
internal val AssetActionRowMaxSpacing = 20.dp

/**
 * Row of [AssetActionButton]s that always fits the width it is given.
 *
 * The gap closes first, from [maxSpacing] down to [AssetActionRowMinSpacing]. Only once that is
 * spent do the buttons themselves give way, and then all of them equally — with five actions the
 * last one used to be the only one squeezed, which clipped it off the sheet and broke its label
 * across three lines.
 */
@Composable
internal fun AssetActionRow(
    actions: List<AssetActionItem>,
    modifier: Modifier = Modifier,
    maxSpacing: Dp = AssetActionRowMaxSpacing,
) {
    val labelStyle = Theme.brockmann.supplementary.caption
    val labels = actions.map { stringResource(it.action.titleRes) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // A label is free to be wider than its icon box — "Intercambiar" is, in Spanish — so the
    // natural width of a button is whichever of the two is wider.
    val naturalWidths =
        remember(labels, labelStyle, density, textMeasurer) {
            labels.map { label ->
                val labelWidth =
                    with(density) { textMeasurer.measure(label, labelStyle).size.width.toDp() }
                maxOf(AssetActionIconBoxSize, labelWidth)
            }
        }

    BoxWithConstraints(modifier = modifier) {
        val layout =
            calculateAssetActionRowLayout(
                availableWidth = maxWidth,
                naturalWidths = naturalWidths,
                maxSpacing = maxSpacing,
            )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(layout.spacing, alignment = Alignment.CenterHorizontally),
        ) {
            actions.forEach { item ->
                val buttonWidth = layout.buttonWidth
                AssetActionButton(
                    modifier = if (buttonWidth == null) Modifier else Modifier.width(buttonWidth),
                    action = item.action,
                    isSelected = item.isSelected,
                    iconBoxSize =
                        if (buttonWidth == null) AssetActionIconBoxSize
                        else minOf(AssetActionIconBoxSize, buttonWidth),
                    onClick = item.onClick,
                )
            }
        }
    }
}

/** @param buttonWidth width every button is held to, or null to let each keep its natural width. */
internal data class AssetActionRowLayout(val spacing: Dp, val buttonWidth: Dp?)

internal fun calculateAssetActionRowLayout(
    availableWidth: Dp,
    naturalWidths: List<Dp>,
    minSpacing: Dp = AssetActionRowMinSpacing,
    maxSpacing: Dp = AssetActionRowMaxSpacing,
): AssetActionRowLayout {
    val gaps = naturalWidths.size - 1
    if (gaps <= 0) return AssetActionRowLayout(spacing = 0.dp, buttonWidth = null)

    val freeSpace = availableWidth - naturalWidths.fold(0.dp) { total, width -> total + width }
    val spacingFromFreeSpace = freeSpace / gaps
    if (spacingFromFreeSpace >= minSpacing) {
        return AssetActionRowLayout(
            spacing = minOf(spacingFromFreeSpace, maxSpacing),
            buttonWidth = null,
        )
    }

    val sharedWidth = (availableWidth - minSpacing * gaps) / naturalWidths.size
    return AssetActionRowLayout(spacing = minSpacing, buttonWidth = maxOf(sharedWidth, 0.dp))
}
