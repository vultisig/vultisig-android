package com.vultisig.wallet.ui.components.chart

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ChartRange
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ChartRangePicker(
    selectedRange: ChartRange,
    onRangeSelected: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 50))
                .background(Theme.v2.colors.backgrounds.tertiary)
                .padding(all = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ChartRange.entries.forEach { range ->
            ChartRangeTab(
                label = stringResource(range.labelRes()),
                isSelected = range == selectedRange,
                onClick = { onRangeSelected(range) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChartRangeTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by
        animateColorAsState(
            targetValue = if (isSelected) Theme.v2.colors.buttons.primary else Color.Transparent,
            label = "chartRangeBackground",
        )
    val textColor by
        animateColorAsState(
            targetValue =
                if (isSelected) Theme.v2.colors.text.inverse else Theme.v2.colors.text.tertiary,
            label = "chartRangeText",
        )

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(backgroundColor)
                .selectable(
                    selected = isSelected,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                )
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = Theme.brockmann.body.s.medium, color = textColor)
    }
}

private fun ChartRange.labelRes(): Int =
    when (this) {
        ChartRange.ONE_DAY -> R.string.chart_range_1d
        ChartRange.ONE_WEEK -> R.string.chart_range_1w
        ChartRange.ONE_MONTH -> R.string.chart_range_1m
        ChartRange.ONE_YEAR -> R.string.chart_range_1y
        ChartRange.ALL -> R.string.chart_range_all
    }

@Preview
@Composable
private fun ChartRangePickerPreview() {
    ChartRangePicker(selectedRange = ChartRange.ONE_DAY, onRangeSelected = {})
}
