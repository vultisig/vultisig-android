package com.vultisig.wallet.ui.screens.swap.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.theme.Theme

/**
 * VULT-tier and referral discount rows shared by the swap form's fee breakdown and the verify swap
 * screen so both render the discount identically (#5358). Each row is a no-op unless both its bps
 * and pre-formatted fiat value are present, so a co-signer that can't resolve the initiator's tier
 * simply shows nothing.
 */
@Composable
internal fun VultDiscountRow(
    vultBpsDiscount: Int?,
    tierType: TierType?,
    fiatValue: String?,
    modifier: Modifier = Modifier,
) {
    if (vultBpsDiscount == null || fiatValue == null) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VultDiscountTier(vultBpsDiscount = vultBpsDiscount, tierType = tierType)

        Text(
            text = "-$fiatValue",
            color = Theme.v2.colors.text.secondary,
            style = Theme.brockmann.supplementary.caption,
        )
    }
}

@Composable
internal fun ReferralDiscountRow(
    referralBpsDiscount: Int?,
    fiatValue: String?,
    modifier: Modifier = Modifier,
) {
    if (referralBpsDiscount == null || fiatValue == null) return
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        UiIcon(
            drawableResId = R.drawable.referral_code,
            size = 16.dp,
            tint = Theme.v2.colors.border.primaryAccent4,
        )
        UiSpacer(size = 4.dp)

        Text(
            text = stringResource(R.string.swap_form_referral_discount_bps, referralBpsDiscount),
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.supplementary.caption,
        )

        UiSpacer(weight = 1f)

        Text(
            text = "-$fiatValue",
            color = Theme.v2.colors.text.secondary,
            style = Theme.brockmann.supplementary.caption,
        )
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
