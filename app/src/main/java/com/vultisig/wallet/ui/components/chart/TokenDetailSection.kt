package com.vultisig.wallet.ui.components.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

private val CardBorderWidth = 1.dp

/**
 * The rounded surface the token detail sheet's blocks sit on: a filled card with a hairline border,
 * matching the list container the same sheet uses on iOS. Used bare by the chart card and with a
 * title by [TokenDetailSection].
 */
@Composable
internal fun TokenDetailCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Theme.v2.radius.xl)
                .background(Theme.v2.colors.backgrounds.surface1)
                .border(
                    width = CardBorderWidth,
                    color = Theme.v2.colors.border.light,
                    shape = Theme.v2.radius.xl,
                ),
        content = content,
    )
}

/**
 * A titled card grouping the token detail sheet's rows — market stats, price range, token info. The
 * title sits outside the card, in caption/tertiary, so the card reads as one block of values rather
 * than a heading competing with the rows under it.
 *
 * Rows are passed as a list rather than a content lambda so the divider between them belongs to the
 * container: only the container knows which row is last, and a trailing divider above the card's
 * bottom edge is the one thing this layout must not draw.
 */
@Composable
internal fun TokenDetailSection(
    title: String,
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )
        UiSpacer(size = 8.dp)
        TokenDetailCard {
            rows.forEachIndexed { index, row ->
                row()
                if (index != rows.lastIndex) {
                    UiHorizontalDivider(
                        color = Theme.v2.colors.border.light,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
