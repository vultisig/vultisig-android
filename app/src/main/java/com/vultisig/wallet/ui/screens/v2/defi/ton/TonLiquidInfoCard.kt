package com.vultisig.wallet.ui.screens.v2.defi.ton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme

/** One label/value line of a [TonLiquidInfoCard]. */
internal data class TonLiquidInfoRow(
    val title: String,
    val value: String,
    val valueColor: Color? = null,
)

/**
 * The bordered detail card the Tonstakers stake and unstake screens show under the amount: pool,
 * APY, fee and the preview of what the pool pays or mints. Rows are divided, values right-aligned.
 */
@Composable
internal fun TonLiquidInfoCard(rows: List<TonLiquidInfoRow>, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Theme.v2.radius.xl)
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(1.dp, Theme.v2.colors.border.light, Theme.v2.radius.xl)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) UiHorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = row.title,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
                Text(
                    text = row.value,
                    style = Theme.brockmann.body.m.medium,
                    color = row.valueColor ?: Theme.v2.colors.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
