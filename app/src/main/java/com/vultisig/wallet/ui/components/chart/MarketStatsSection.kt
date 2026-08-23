package com.vultisig.wallet.ui.components.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.MarketStatsUiModel

/**
 * Market cap, rank, volume, FDV and supply. While [isLoading] and [stats] is still null, shows a
 * placeholder skeleton (fixed row set) so the sheet's layout doesn't jump once the fetch resolves;
 * once loaded, only the fields CoinGecko actually returned are shown. Renders nothing once loaded
 * if every field came back null.
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
                    stats.volume24h?.let {
                        stringResource(R.string.token_details_volume_24h) to it
                    },
                    stats.fullyDilutedValuation?.let {
                        stringResource(R.string.token_details_fully_diluted_valuation) to it
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
                    stringResource(R.string.token_details_volume_24h) to null,
                    stringResource(R.string.token_details_fully_diluted_valuation) to null,
                    stringResource(R.string.token_details_circulating_supply) to null,
                    stringResource(R.string.token_details_max_supply) to null,
                )
            else -> emptyList()
        }
    if (rows.isEmpty()) return

    TokenDetailSection(
        title = stringResource(R.string.token_details_market_stats_title),
        rows = rows.map { (label, value) -> { TokenDetailRow(label = label, value = value) } },
        modifier = modifier,
    )
}
