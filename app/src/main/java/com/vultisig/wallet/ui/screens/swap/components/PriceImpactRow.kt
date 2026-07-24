package com.vultisig.wallet.ui.screens.swap.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.swap.PriceImpactLevel
import com.vultisig.wallet.ui.theme.Theme

/**
 * Price Impact row shared by the swap form's fee breakdown, the verify screen and the
 * completed-swap overview so the liquidity cost excluded from the fee total reads identically on
 * all three (#5335). A no-op unless both the percentage and its tier are present — EVM aggregators
 * report no impact, and a co-signer rebuilding the transaction from the payload has no quote to
 * read it from.
 */
@Composable
internal fun PriceImpactRow(
    priceImpactPercent: String?,
    priceImpactLevel: PriceImpactLevel?,
    modifier: Modifier = Modifier,
) {
    if (priceImpactPercent == null || priceImpactLevel == null) return

    val levelLabel =
        when (priceImpactLevel) {
            PriceImpactLevel.GOOD -> R.string.swap_price_impact_good
            PriceImpactLevel.AVERAGE -> R.string.swap_price_impact_average
            PriceImpactLevel.HIGH -> R.string.swap_price_impact_high
        }
    val levelColor =
        when (priceImpactLevel) {
            PriceImpactLevel.GOOD -> Theme.v2.colors.alerts.success
            PriceImpactLevel.AVERAGE -> Theme.v2.colors.alerts.warning
            PriceImpactLevel.HIGH -> Theme.v2.colors.alerts.error
        }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = stringResource(R.string.swap_form_price_impact_title),
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.supplementary.caption,
        )

        Text(
            text = "$priceImpactPercent (${stringResource(levelLabel)})",
            color = levelColor,
            style = Theme.brockmann.supplementary.caption,
        )
    }
}
