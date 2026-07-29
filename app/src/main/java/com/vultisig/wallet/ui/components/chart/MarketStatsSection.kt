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
import com.vultisig.wallet.ui.models.MarketStatsUiModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * Market cap, rank, FDV, 24h volume and supply. While [isLoading] and [stats] is still null, shows
 * a placeholder skeleton (fixed row set) so the sheet's layout doesn't jump once the fetch
 * resolves; once loaded, only the fields CoinGecko actually returned are shown. Renders nothing
 * once loaded if every field came back null.
 */
@Composable
internal fun MarketStatsSection(
    stats: MarketStatsUiModel?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val rows: List<Pair<String, String?>> =
        when {
            stats != null ->
                listOfNotNull(
                    stats.marketCap?.let {
                        stringResource(R.string.token_details_market_cap) to it
                    },
                    stats.marketCapRank?.let {
                        stringResource(R.string.token_details_market_cap_rank) to it
                    },
                    stats.fullyDilutedValuation?.let {
                        stringResource(R.string.token_details_fully_diluted_valuation) to it
                    },
                    stats.volume24h?.let {
                        stringResource(R.string.token_details_volume_24h) to it
                    },
                    stats.circulatingSupply?.let {
                        stringResource(R.string.token_details_circulating_supply) to it
                    },
                    stats.maxSupply?.let { stringResource(R.string.token_details_max_supply) to it },
                )
            isLoading ->
                listOf(
                    stringResource(R.string.token_details_market_cap) to null,
                    stringResource(R.string.token_details_market_cap_rank) to null,
                    stringResource(R.string.token_details_fully_diluted_valuation) to null,
                    stringResource(R.string.token_details_volume_24h) to null,
                    stringResource(R.string.token_details_circulating_supply) to null,
                    stringResource(R.string.token_details_max_supply) to null,
                )
            else -> emptyList()
        }
    if (rows.isEmpty()) return

    UiSpacer(size = 24.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.token_details_market_stats_title),
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
