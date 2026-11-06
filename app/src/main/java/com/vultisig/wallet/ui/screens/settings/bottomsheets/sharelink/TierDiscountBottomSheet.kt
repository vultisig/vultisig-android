package com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.BRONZE_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.DIAMOND_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.GOLD_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.PLATINUM_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_DISCOUNT_BPS
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.SettingsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.models.swap.discountPercentOfListFee
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.roundToInt

@Composable
internal fun TierDiscountBottomSheet(
    tier: TierType,
    onContinue: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    SettingsModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Theme.v2.colors.backgrounds.surface1,
    ) {
        TierDiscountBottomSheetContent(tier = tier, onContinue = onContinue)
    }
}

@Composable
internal fun TierDiscountBottomSheetContent(tier: TierType, onContinue: () -> Unit) {
    val tierStyle = getStyleByTier(tier)

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(Theme.v2.colors.backgrounds.secondary)
                .fadingDotPattern()
                .clip(
                    Theme.v2.radius.xl.shape.copy(
                        bottomStart = CornerSize(0.dp),
                        bottomEnd = CornerSize(0.dp),
                    )
                )
    ) {
        Column(
            modifier =
                Modifier.padding(start = 30.dp, top = 33.dp, end = 30.dp, bottom = 32.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = tierStyle.logoTier),
                contentDescription = null,
                modifier = Modifier.size(46.dp),
            )

            UiSpacer(26.dp)

            Text(
                text = "${tierStyle.titlePart1} ${tierStyle.titlePart2} ${tierStyle.titlePart3}",
                style = Theme.brockmann.headings.title1,
                textAlign = TextAlign.Center,
                color = Theme.v2.colors.text.secondary,
            )

            UiSpacer(20.dp)

            Text(
                text =
                    buildAnnotatedString {
                        append(tierStyle.descriptionPart1)
                        pushStyle(
                            Theme.brockmann.body.s.regular
                                .copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Theme.v2.colors.text.primary,
                                )
                                .toSpanStyle()
                        )
                        append(" " + tierStyle.descriptionPart2 + " ")
                        pop()
                        append(tierStyle.descriptionPart3)
                    },
                style = Theme.brockmann.body.s.regular,
                textAlign = TextAlign.Center,
                color = Theme.v2.colors.text.secondary,
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(20.dp)

            VsButton(
                label = stringResource(R.string.vault_tier_unlock),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun Modifier.fadingDotPattern(): Modifier = drawBehind {
    // Draw dots with 0.05 alpha, starting on 80% of the height
    // we gradually make them dissapear to match the bottom bar color
    val baseColor = Color.White
    val spacingPx = 12.dp.toPx()
    val radiusPx = 1.dp.toPx()

    val fadeStartY = size.height * 0.8f
    val fadeEndY = size.height
    val maxY = size.height.toInt()
    val maxX = size.width.toInt()

    // Precompute step size to avoid float rounding issues
    val stepY = spacingPx.roundToInt().coerceAtLeast(1)
    val stepX = spacingPx.roundToInt().coerceAtLeast(1)

    for (y in 0..maxY step stepY) {
        val alphaFactor =
            when {
                y < fadeStartY -> 1f
                y > fadeEndY -> 0f
                else -> 1f - ((y - fadeStartY) / (fadeEndY - fadeStartY))
            }

        val alpha = (0.05f * alphaFactor).coerceIn(0f, 0.05f)
        if (alpha <= 0f) continue

        for (x in 0..maxX step stepX) {
            drawCircle(
                color = baseColor.copy(alpha = alpha),
                radius = radiusPx,
                center = Offset(x.toFloat(), y.toFloat()),
            )
        }
    }
}

@Composable
private fun getStyleByTier(tier: TierType): BottomSheetTierStyle {
    return when (tier) {
        TierType.BRONZE ->
            BottomSheetTierStyle(
                logoTier = R.drawable.tier_bronze,
                titlePart1 = stringResource(R.string.vault_tier_unlock_bronze_part1),
                titlePart2 = stringResource(R.string.vault_tier_unlock_bronze_part2),
                titlePart3 = stringResource(R.string.vault_tier_unlock_bronze_part3),
                descriptionPart1 = stringResource(R.string.vault_tier_bronze_description_part1),
                descriptionPart2 = feeDiscountDescription(BRONZE_DISCOUNT_BPS),
                descriptionPart3 = stringResource(R.string.vault_tier_bronze_description_part3),
            )

        TierType.SILVER ->
            BottomSheetTierStyle(
                logoTier = R.drawable.tier_silver,
                titlePart1 = stringResource(R.string.vault_tier_unlock_silver_part1),
                titlePart2 = stringResource(R.string.vault_tier_unlock_silver_part2),
                titlePart3 = stringResource(R.string.vault_tier_unlock_silver_part3),
                descriptionPart1 = stringResource(R.string.vault_tier_silver_description_part1),
                descriptionPart2 = feeDiscountDescription(SILVER_DISCOUNT_BPS),
                descriptionPart3 = stringResource(R.string.vault_tier_silver_description_part3),
            )

        TierType.GOLD ->
            BottomSheetTierStyle(
                logoTier = R.drawable.tier_gold,
                titlePart1 = stringResource(R.string.vault_tier_unlock_gold_part1),
                titlePart2 = stringResource(R.string.vault_tier_unlock_gold_part2),
                titlePart3 = stringResource(R.string.vault_tier_unlock_gold_part3),
                descriptionPart1 = stringResource(R.string.vault_tier_gold_description_part1),
                descriptionPart2 = feeDiscountDescription(GOLD_DISCOUNT_BPS),
                descriptionPart3 = stringResource(R.string.vault_tier_gold_description_part3),
            )

        TierType.PLATINUM ->
            BottomSheetTierStyle(
                logoTier = R.drawable.tier_platinum,
                titlePart1 = stringResource(R.string.vault_tier_unlock_platinum_part1),
                titlePart2 = stringResource(R.string.vault_tier_unlock_platinum_part2),
                titlePart3 = stringResource(R.string.vault_tier_unlock_platinum_part3),
                descriptionPart1 = stringResource(R.string.vault_tier_platinum_description_part1),
                descriptionPart2 = feeDiscountDescription(PLATINUM_DISCOUNT_BPS),
                descriptionPart3 = stringResource(R.string.vault_tier_platinum_description_part3),
            )

        TierType.DIAMOND ->
            BottomSheetTierStyle(
                logoTier = R.drawable.tier_diamond,
                titlePart1 = stringResource(R.string.vault_tier_unlock_diamond_part1),
                titlePart2 = stringResource(R.string.vault_tier_unlock_diamond_part2),
                titlePart3 = stringResource(R.string.vault_tier_unlock_diamond_part3),
                descriptionPart1 = stringResource(R.string.vault_tier_diamond_description_part1),
                descriptionPart2 = feeDiscountDescription(DIAMOND_DISCOUNT_BPS),
                descriptionPart3 = stringResource(R.string.vault_tier_diamond_description_part3),
            )

        TierType.ULTIMATE ->
            BottomSheetTierStyle(
                logoTier = R.drawable.tier_ultimate,
                titlePart1 = stringResource(R.string.vault_tier_unlock_ultimate_part1),
                titlePart2 = stringResource(R.string.vault_tier_unlock_ultimate_part2),
                titlePart3 = stringResource(R.string.vault_tier_unlock_ultimate_part3),
                descriptionPart1 = stringResource(R.string.vault_tier_ultimate_description_part1),
                descriptionPart2 = stringResource(R.string.vault_tier_ultimate_description_part2),
                descriptionPart3 = stringResource(R.string.vault_tier_ultimate_description_part3),
            )
    }
}

@Composable
private fun feeDiscountDescription(discountBps: Int): String =
    stringResource(
        R.string.vault_tier_fee_discount_description_part2,
        discountPercentOfListFee(discountBps),
    )

internal data class BottomSheetTierStyle(
    val logoTier: Int,
    val titlePart1: String,
    val titlePart2: String,
    val titlePart3: String,
    val descriptionPart1: String,
    val descriptionPart2: String,
    val descriptionPart3: String,
)

@Preview(showBackground = true)
@Composable
private fun TierDiscountBottomSheetPreview() {
    TierDiscountBottomSheetContent(tier = TierType.GOLD, onContinue = {})
}

@Preview(showBackground = true, name = "Bronze Tier")
@Composable
private fun TierDiscountBottomSheetBronzePreview() {
    TierDiscountBottomSheetContent(tier = TierType.BRONZE, onContinue = {})
}

@Preview(showBackground = true, name = "Platinum Tier")
@Composable
private fun TierDiscountBottomSheetPlatinumPreview() {
    TierDiscountBottomSheetContent(tier = TierType.PLATINUM, onContinue = {})
}

@Preview(showBackground = true, name = "Platinum Tier")
@Composable
private fun TierDiscountBottomSheetDiamondPreview() {
    TierDiscountBottomSheetContent(tier = TierType.DIAMOND, onContinue = {})
}

@Preview(showBackground = true, name = "Ultimate Tier")
@Composable
private fun TierDiscountBottomSheetUltimatePreview() {
    TierDiscountBottomSheetContent(tier = TierType.ULTIMATE, onContinue = {})
}
