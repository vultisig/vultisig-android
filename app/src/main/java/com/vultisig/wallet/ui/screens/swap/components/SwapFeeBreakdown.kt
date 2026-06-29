package com.vultisig.wallet.ui.screens.swap.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormDetails2
import com.vultisig.wallet.ui.models.swap.DiscountInfo
import com.vultisig.wallet.ui.models.swap.FeeBreakdown
import com.vultisig.wallet.ui.models.swap.PriceImpactLevel
import com.vultisig.wallet.ui.models.swap.QuoteDisplay
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

/**
 * Provider and fee/discount breakdown panel shown once a quote is loading or available.
 *
 * Renders the provider row, the expandable total-fees row, and — when expanded — the gas, swap,
 * outbound, VULT-tier and referral discount details. Manages its own expand/collapse state.
 *
 * @param isLoading whether a quote is currently loading; drives the skeleton placeholders.
 * @param quoteDisplay quote-side values (provider label and quote availability).
 * @param feeBreakdown network and swap fee values rendered in the details panel.
 * @param discountInfo VULT-tier and referral discount values rendered in the details panel.
 */
@Composable
internal fun SwapFeeBreakdown(
    isLoading: Boolean,
    quoteDisplay: QuoteDisplay,
    feeBreakdown: FeeBreakdown,
    discountInfo: DiscountInfo,
) {
    var isFeeDetailsExpanded by remember { mutableStateOf(false) }

    val rotationAngle by
        animateFloatAsState(
            targetValue = if (isFeeDetailsExpanded) 180f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "caretRotation",
        )

    AnimatedVisibility(visible = isLoading || quoteDisplay.hasQuote) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            FormDetails2(
                title = stringResource(R.string.swap_screen_provider_title),
                value = quoteDisplay.provider.asString(),
                placeholder =
                    if (isLoading) {
                        { loadingPlaceholder() }
                    } else null,
            )

            FormDetails2(
                modifier =
                    Modifier.clickable(onClick = { isFeeDetailsExpanded = !isFeeDetailsExpanded }),
                title = stringResource(R.string.swap_form_total_fees_title),
                valueComposable =
                    if (isLoading) {
                        { loadingPlaceholder() }
                    } else {
                        {
                            Row {
                                Text(
                                    text = feeBreakdown.totalFee,
                                    color = Theme.v2.colors.text.secondary,
                                    style = Theme.brockmann.supplementary.caption,
                                    textAlign = TextAlign.End,
                                )

                                UiSpacer(size = 8.dp)
                                UiIcon(
                                    drawableResId = R.drawable.ic_caret_down,
                                    tint = Theme.v2.colors.text.primary,
                                    size = 16.dp,
                                    modifier = Modifier.rotate(rotationAngle),
                                )
                            }
                        }
                    },
            )

            AnimatedVisibility(visible = isFeeDetailsExpanded && isLoading.not()) {
                Row(modifier = Modifier.height(IntrinsicSize.Max)) {
                    Box(
                        modifier =
                            Modifier.width(1.5.dp)
                                .fillMaxHeight()
                                .background(
                                    color = Theme.v2.colors.border.primaryAccent4,
                                    shape = CircleShape,
                                )
                    )

                    UiSpacer(size = 8.dp)

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormDetails2(
                            modifier = Modifier.fillMaxWidth(),
                            title =
                                buildAnnotatedString {
                                    append(stringResource(R.string.swap_form_gas_title))
                                },
                            value =
                                buildAnnotatedString {
                                    withStyle(
                                        style = SpanStyle(color = Theme.v2.colors.neutrals.n100)
                                    ) {
                                        append(feeBreakdown.networkFee)
                                    }
                                    append(" ")
                                    withStyle(
                                        style = SpanStyle(color = Theme.v2.colors.neutrals.n400)
                                    ) {
                                        append(
                                            if (feeBreakdown.networkFeeFiat.isNotEmpty())
                                                "(~${feeBreakdown.networkFeeFiat})"
                                            else ""
                                        )
                                    }
                                },
                            placeholder =
                                if (isLoading) {
                                    { loadingPlaceholder() }
                                } else null,
                        )

                        val feeTitle =
                            feeBreakdown.swapFeePercent?.let {
                                stringResource(
                                    R.string.swap_form_estimated_fees_with_percent_title,
                                    it,
                                )
                            } ?: stringResource(R.string.swap_form_estimated_fees_title)
                        FormDetails2(
                            title = feeTitle,
                            value = feeBreakdown.fee,
                            placeholder =
                                if (isLoading) {
                                    { loadingPlaceholder() }
                                } else null,
                        )

                        if (feeBreakdown.outboundFee != null) {
                            FormDetails2(
                                title = stringResource(R.string.swap_form_outbound_fee_title),
                                value = feeBreakdown.outboundFee,
                            )
                        }

                        if (
                            discountInfo.vultBpsDiscount != null &&
                                discountInfo.vultBpsDiscountFiatValue != null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                VultDiscountTier(
                                    vultBpsDiscount = discountInfo.vultBpsDiscount,
                                    tierType = discountInfo.tierType,
                                )

                                Text(
                                    text = "-${discountInfo.vultBpsDiscountFiatValue}",
                                    color = Theme.v2.colors.text.secondary,
                                    style = Theme.brockmann.supplementary.caption,
                                )
                            }
                        }

                        if (
                            discountInfo.referralBpsDiscount != null &&
                                discountInfo.referralBpsDiscountFiatValue != null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UiIcon(
                                    drawableResId = R.drawable.referral_code,
                                    size = 16.dp,
                                    tint = Theme.v2.colors.border.primaryAccent4,
                                )
                                UiSpacer(size = 4.dp)

                                Text(
                                    text =
                                        stringResource(
                                            R.string.swap_form_referral_discount_bps,
                                            discountInfo.referralBpsDiscount,
                                        ),
                                    color = Theme.v2.colors.text.tertiary,
                                    style = Theme.brockmann.supplementary.caption,
                                )

                                UiSpacer(weight = 1f)

                                Text(
                                    text = "-${discountInfo.referralBpsDiscountFiatValue}",
                                    color = Theme.v2.colors.text.secondary,
                                    style = Theme.brockmann.supplementary.caption,
                                )
                            }
                        }

                        if (
                            feeBreakdown.priceImpactPercent != null &&
                                feeBreakdown.priceImpactLevel != null
                        ) {
                            val levelLabel =
                                when (feeBreakdown.priceImpactLevel) {
                                    PriceImpactLevel.GOOD -> R.string.swap_price_impact_good
                                    PriceImpactLevel.AVERAGE -> R.string.swap_price_impact_average
                                    PriceImpactLevel.HIGH -> R.string.swap_price_impact_high
                                }
                            val levelColor =
                                when (feeBreakdown.priceImpactLevel) {
                                    PriceImpactLevel.GOOD -> Theme.v2.colors.alerts.success
                                    PriceImpactLevel.AVERAGE -> Theme.v2.colors.alerts.warning
                                    PriceImpactLevel.HIGH -> Theme.v2.colors.alerts.error
                                }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.swap_form_price_impact_title),
                                    color = Theme.v2.colors.text.tertiary,
                                    style = Theme.brockmann.supplementary.caption,
                                )

                                Text(
                                    text =
                                        "${feeBreakdown.priceImpactPercent} " +
                                            "(${stringResource(levelLabel)})",
                                    color = levelColor,
                                    style = Theme.brockmann.supplementary.caption,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VultDiscountTier(vultBpsDiscount: Int, tierType: TierType?) {
    val (title, logo) =
        when (tierType) {
            TierType.BRONZE -> R.string.vault_tier_bronze to R.drawable.type_bronze_tier__size_small
            TierType.SILVER -> R.string.vault_tier_silver to R.drawable.type_silver_tier__size_small
            TierType.GOLD -> R.string.vault_tier_gold to R.drawable.type_gold_tier__size_small
            TierType.PLATINUM ->
                R.string.vault_tier_platinum to R.drawable.type_platinum_tier__size_small

            TierType.DIAMOND -> R.string.vault_tier_diamond to R.drawable.type_diamond__size_small
            TierType.ULTIMATE -> R.string.vault_tier_ultimate to R.drawable.tier_ultimate
            else -> null to null
        }

    if (title == null || logo == null) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "rotation")

        val rotation by
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 10000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "rotation",
            )

        Image(
            painterResource(logo),
            contentDescription = null,
            modifier = Modifier.size(16.dp).rotate(rotation),
        )

        Text(
            text =
                stringResource(
                    R.string.swap_form_vult_discount_bps,
                    stringResource(title),
                    vultBpsDiscount,
                ),
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.supplementary.caption,
        )
    }
}
