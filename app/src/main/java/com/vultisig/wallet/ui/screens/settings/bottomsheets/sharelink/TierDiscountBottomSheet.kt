package com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierDiscountBottomSheet(
    tier: TierType,
    onContinue: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Theme.colors.backgrounds.secondary,
        shape = RoundedCornerShape(24.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        TierDiscountBottomSheetContent(
            tier = tier,
            onContinue = onContinue,
        )
    }
}

@Composable
internal fun TierDiscountBottomSheetContent(
    tier: TierType,
    onContinue: () -> Unit,
) {
    val tierStyle = getStyleByTier(tier)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.colors.backgrounds.secondary)
            .drawBehind {
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
                    val alphaFactor = when {
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
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                    }
                }
            }
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = tierStyle.logoTier),
                    contentDescription = "image",
                )
            }

            UiSpacer(32.dp)

            Text(
                text = buildAnnotatedString {
                    append(tierStyle.titlePart1 + " ")
                    pushStyle(Theme.brockmann.headings.title1.copy(color = tierStyle.tierColor).toSpanStyle())
                    append(tierStyle.titlePart2)
                    pop()
                    append(" "+ tierStyle.titlePart3)
                },
                style = Theme.brockmann.headings.title1,
                textAlign = TextAlign.Center,
                color = Theme.colors.text.primary,
            )

            UiSpacer(32.dp)

            Text(
                text = buildAnnotatedString {
                    append(tierStyle.descriptionPart1)
                    pushStyle(Theme.brockmann.body.s.regular.copy(fontWeight = FontWeight.Bold).toSpanStyle())
                    append(" " + tierStyle.descriptionPart2 + " ")
                    pop()
                    append(tierStyle.descriptionPart3)
                },
                style = Theme.brockmann.body.s.regular,
                textAlign = TextAlign.Center,
                color = Theme.colors.text.primary,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
            )

            UiSpacer(32.dp)

            VsButton(
                label = stringResource(R.string.vault_tier_unlock),
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun getStyleByTier(tier: TierType) : BottomSheetTierStyle {
    return when (tier){
        TierType.BRONZE -> BottomSheetTierStyle(
            logoTier = R.drawable.tier_bronze_bottomsheet,
            titlePart1 = stringResource(R.string.vault_tier_unlock_bronze_part1),
            titlePart2 = stringResource(R.string.vault_tier_unlock_bronze_part2),
            titlePart3 = stringResource(R.string.vault_tier_unlock_bronze_part3),
            tierColor = Color(0xFFFF6333),
            descriptionPart1 = stringResource(R.string.vault_tier_bronze_description_part1),
            descriptionPart2 = stringResource(R.string.vault_tier_bronze_description_part2),
            descriptionPart3 = stringResource(R.string.vault_tier_bronze_description_part3),
        )
        TierType.SILVER -> BottomSheetTierStyle(
            logoTier = R.drawable.tier_silver_bottomsheet,
            titlePart1 = stringResource(R.string.vault_tier_unlock_silver_part1),
            titlePart2 = stringResource(R.string.vault_tier_unlock_silver_part2),
            titlePart3 = stringResource(R.string.vault_tier_unlock_silver_part3),
            tierColor = Color(0xFFC9D6E8),
            descriptionPart1 = stringResource(R.string.vault_tier_silver_description_part1),
            descriptionPart2 = stringResource(R.string.vault_tier_silver_description_part2),
            descriptionPart3 = stringResource(R.string.vault_tier_silver_description_part3),
        )
        TierType.GOLD -> BottomSheetTierStyle(
            logoTier = R.drawable.tier_gold_bottomsheet,
            titlePart1 = stringResource(R.string.vault_tier_unlock_gold_part1),
            titlePart2 = stringResource(R.string.vault_tier_unlock_gold_part2),
            titlePart3 = stringResource(R.string.vault_tier_unlock_gold_part3),
            tierColor = Color(0xFFFFC25C),
            descriptionPart1 = stringResource(R.string.vault_tier_gold_description_part1),
            descriptionPart2 = stringResource(R.string.vault_tier_gold_description_part2),
            descriptionPart3 = stringResource(R.string.vault_tier_gold_description_part3),
        )
        TierType.PLATINUM -> BottomSheetTierStyle(
            logoTier = R.drawable.tier_platinum_bottomsheet,
            titlePart1 = stringResource(R.string.vault_tier_unlock_platinum_part1),
            titlePart2 = stringResource(R.string.vault_tier_unlock_platinum_part2),
            titlePart3 = stringResource(R.string.vault_tier_unlock_platinum_part3),
            tierColor = Color(0xFF38CDCD),
            descriptionPart1 = stringResource(R.string.vault_tier_platinum_description_part1),
            descriptionPart2 = stringResource(R.string.vault_tier_platinum_description_part2),
            descriptionPart3 = stringResource(R.string.vault_tier_platinum_description_part3),
        )
    }
}

internal data class BottomSheetTierStyle(
    val logoTier: Int,
    val titlePart1: String,
    val titlePart2: String,
    val titlePart3: String,
    val tierColor: Color,
    val descriptionPart1: String,
    val descriptionPart2: String,
    val descriptionPart3: String,
)

@Preview(showBackground = true)
@Composable
private fun TierDiscountBottomSheetPreview() {
    TierDiscountBottomSheetContent(
        tier = TierType.GOLD,
        onContinue = {}
    )
}

@Preview(showBackground = true, name = "Bronze Tier")
@Composable
private fun TierDiscountBottomSheetBronzePreview() {
    TierDiscountBottomSheetContent(
        tier = TierType.BRONZE,
        onContinue = {}
    )
}

@Preview(showBackground = true, name = "Platinum Tier")
@Composable
private fun TierDiscountBottomSheetPlatinumPreview() {
    TierDiscountBottomSheetContent(
        tier = TierType.PLATINUM,
        onContinue = {}
    )
}
