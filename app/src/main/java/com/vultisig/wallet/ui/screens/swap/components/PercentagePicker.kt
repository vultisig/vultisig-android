package com.vultisig.wallet.ui.screens.swap.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

/**
 * Quick-amount picker row shown above the keyboard while the source amount is being edited.
 *
 * @param enableMaxAmount whether the trailing MAX shortcut should be shown.
 * @param onSelectSrcPercentage invoked with the chosen fraction (0.25f, 0.5f, 0.75f, 1f).
 */
@Composable
internal fun PercentagePicker(enableMaxAmount: Boolean, onSelectSrcPercentage: (Float) -> Unit) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = Theme.v2.colors.border.light)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier.fillMaxWidth()
                    .background(color = Theme.v2.colors.backgrounds.secondary)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
        ) {
            PercentageItem(title = "25%", onClick = { onSelectSrcPercentage(0.25f) })
            PercentageItem(title = "50%", onClick = { onSelectSrcPercentage(0.5f) })
            PercentageItem(title = "75%", onClick = { onSelectSrcPercentage(0.75f) })
            if (enableMaxAmount)
                PercentageItem(title = "MAX", onClick = { onSelectSrcPercentage(1f) })
        }
    }
}

@Composable
private fun RowScope.PercentageItem(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.text.primary,
        textAlign = TextAlign.Center,
        modifier =
            Modifier.clickable(onClick = onClick)
                .background(
                    color = Theme.v2.colors.backgrounds.tertiary_2,
                    shape = RoundedCornerShape(99.dp),
                )
                .padding(all = 8.dp)
                .weight(1f),
    )
}
