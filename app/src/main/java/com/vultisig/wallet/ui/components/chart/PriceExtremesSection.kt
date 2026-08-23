package com.vultisig.wallet.ui.components.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.PriceExtremesUiModel
import com.vultisig.wallet.ui.theme.Theme

private val BandTrackHeight = 4.dp
private val BandMarkerSize = 8.dp

/**
 * The 24h low–high band and the all-time high/low rows. While [isLoading] and [extremes] is still
 * null, shows a placeholder skeleton so the sheet's layout doesn't jump once the fetch resolves;
 * renders nothing once loaded if every field came back null.
 */
@Composable
internal fun PriceExtremesSection(
    extremes: PriceExtremesUiModel?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasBand = extremes?.hasBand() == true
    val hasExtremeRows = extremes?.athPrice != null || extremes?.atlPrice != null
    if (extremes != null && !hasBand && !hasExtremeRows) return
    if (extremes == null && !isLoading) return

    val rows: List<@Composable () -> Unit> = buildList {
        if (extremes == null) {
            // Skeleton: the same three blocks the loaded section shows, with empty values.
            add { PriceRangeBand(low = null, high = null, position = null) }
            add { TokenDetailRow(label = stringResource(R.string.token_details_all_time_high)) }
            add { TokenDetailRow(label = stringResource(R.string.token_details_all_time_low)) }
            return@buildList
        }
        if (hasBand) {
            add {
                PriceRangeBand(
                    low = extremes.low24h,
                    high = extremes.high24h,
                    position = extremes.bandPosition,
                )
            }
        }
        if (extremes.athPrice != null) {
            add {
                TokenDetailRow(
                    label = stringResource(R.string.token_details_all_time_high),
                    value = extremes.athPrice,
                    subValue = caption(extremes.athChangePercent, extremes.athDate),
                )
            }
        }
        if (extremes.atlPrice != null) {
            add {
                TokenDetailRow(
                    label = stringResource(R.string.token_details_all_time_low),
                    value = extremes.atlPrice,
                    subValue = caption(extremes.atlChangePercent, extremes.atlDate),
                )
            }
        }
    }

    TokenDetailSection(
        title = stringResource(R.string.token_details_price_extremes_title),
        rows = rows,
        modifier = modifier,
    )
}

/**
 * The 24h band: a slim track with a marker for where the current price sits between the extremes.
 * "Near the day's high" is what this block exists to say, and a pair of numbers alone doesn't say
 * it.
 */
@Composable
private fun PriceRangeBand(low: String?, high: String?, position: Float?) {
    Column(modifier = Modifier.fillMaxWidth().padding(all = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.token_details_low_24h),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
            )
            UiSpacer(weight = 1f)
            Text(
                text = stringResource(R.string.token_details_high_24h),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
            )
        }

        UiSpacer(size = 8.dp)

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(BandMarkerSize),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(BandTrackHeight)
                        .clip(Theme.v2.radius.pill)
                        .background(Theme.v2.colors.backgrounds.primary)
            )
            if (position != null) {
                // The marker is inset by its own width so it stays inside the track at either end
                // rather than half-hanging off it.
                val travel = maxWidth - BandMarkerSize
                Box(
                    modifier =
                        Modifier.offset(x = travel * position.coerceIn(0f, 1f))
                            .size(BandMarkerSize)
                            .clip(Theme.v2.radius.pill)
                            .background(Theme.v2.colors.primary.accent4)
                )
            }
        }

        UiSpacer(size = 8.dp)

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = low.orEmpty(),
                style = Theme.satoshi.price.caption,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(weight = 1f)
            Text(
                text = high.orEmpty(),
                style = Theme.satoshi.price.caption,
                color = Theme.v2.colors.text.primary,
            )
        }
    }
}

/** "-62.16% · 3 Dec 2024" — how far the price has come from the extreme, and when it was set. */
private fun caption(changePercent: String?, date: String?): String? =
    listOfNotNull(changePercent, date).takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")

@Preview
@Composable
private fun PriceExtremesSectionPreview() {
    PriceExtremesSection(
        extremes =
            PriceExtremesUiModel(
                low24h = "$1,829.34",
                high24h = "$1,929.56",
                bandPosition = 0.2f,
                athPrice = "$4,956.34",
                athDate = "3 Dec 2024",
                athChangePercent = "-62.16%",
                atlPrice = "$0.43",
                atlDate = "3 Oct 2015",
                atlChangePercent = "+23.16%",
            ),
        isLoading = false,
    )
}
