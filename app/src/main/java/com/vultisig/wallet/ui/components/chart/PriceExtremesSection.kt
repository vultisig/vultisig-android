package com.vultisig.wallet.ui.components.chart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenMetaRow
import com.vultisig.wallet.ui.models.PriceExtremesUiModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * 24h low/high and all-time-high/low with their dates. While [isLoading] and [extremes] is still
 * null, shows a placeholder skeleton so the sheet's layout doesn't jump once the fetch resolves;
 * renders nothing once loaded if every field came back null.
 */
@Composable
internal fun PriceExtremesSection(
    extremes: PriceExtremesUiModel?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val rows: List<Pair<String, String?>> =
        when {
            extremes != null ->
                listOfNotNull(
                    extremes.low24h?.let { stringResource(R.string.token_details_low_24h) to it },
                    extremes.high24h?.let { stringResource(R.string.token_details_high_24h) to it },
                    extremes.athPrice?.let {
                        stringResource(R.string.token_details_all_time_high) to
                            it.withDate(extremes.athDate)
                    },
                    extremes.atlPrice?.let {
                        stringResource(R.string.token_details_all_time_low) to
                            it.withDate(extremes.atlDate)
                    },
                )
            isLoading ->
                listOf(
                    stringResource(R.string.token_details_low_24h) to null,
                    stringResource(R.string.token_details_high_24h) to null,
                    stringResource(R.string.token_details_all_time_high) to null,
                    stringResource(R.string.token_details_all_time_low) to null,
                )
            else -> emptyList()
        }
    if (rows.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.token_details_price_extremes_title),
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.text.primary,
        )
        UiSpacer(size = 12.dp)
        TopShineContainer(backgroundColor = Theme.v2.colors.backgrounds.surface1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                rows.forEachIndexed { index, (key, value) ->
                    TokenMetaRow(key = key, value = value)
                    if (index != rows.lastIndex) {
                        UiHorizontalDivider(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private fun String.withDate(date: String?): String = if (date != null) "$this · $date" else this
