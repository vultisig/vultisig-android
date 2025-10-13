package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.screens.v2.components.VsButton
import com.vultisig.wallet.ui.theme.Theme
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun DiscountTiersScreen(
    navController: NavHostController
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.backgrounds.secondary),
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.vault_settings_discounts),
                startIcon = R.drawable.ic_caret_left
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = Theme.colors.borders.light,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.tiers_header),
                    contentDescription = "Provider Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            UiSpacer(size = 24.dp)

            Text(
                text = "Hold VULT to unlock lower trading fees.",
                style = Theme.brockmann.body.s.regular,
                textAlign = TextAlign.Start,
            )

            UiSpacer(size = 16.dp)

            TierCard(TierType.BRONZE)

            TierCard(TierType.SILVER)

            TierCard(TierType.GOLD)

            TierCard(TierType.PLATINIUM)

            UiSpacer(size = 16.dp)
        }
    }
}

@Composable
private fun TierCard(
    tierType: TierType,
    onClickUnlock: () -> Unit,
) {
    val styleTier = getStyleByTier(tierType)

    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = styleTier.gradient,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.colors.backgrounds.neutral)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(styleTier.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(55.dp)
                            .padding(8.dp)
                    )

                    Text(
                        text = styleTier.titleText,
                        style = Theme.brockmann.headings.title1,
                        color = Theme.v2.colors.text.primary,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.normal,
                            shape = RoundedCornerShape(50)
                        )
                        .background(Theme.v2.colors.backgrounds.surface2)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = styleTier.discountText,
                        style = Theme.brockmann.supplementary.footnote,
                        color = Theme.v2.colors.text.primary,
                    )
                }
            }

            UiSpacer(size = 8.dp)

            Text(
                text = stringResource(R.string.vault_tier_hold),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.extraLight
            )

            Text(
                text = styleTier.amountText,
                style = Theme.brockmann.body.l.regular,
                color = Theme.v2.colors.text.primary
            )

            UiSpacer(size = 16.dp)

            VsButton(
                label = "Unlock Tier",
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = onClickUnlock,
            )
        }
    }
}

@Composable
internal fun getStyleByTier(type: TierType): TierStyle {
    return when (type) {
        TierType.BRONZE -> TierStyle(
            icon = R.drawable.tier_bronze,
            titleText = stringResource(R.string.vault_tier_bronze),
            discountText = stringResource(R.string.vault_tier_bronze_discount),
            amountText = formatVultAmount(1000),
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFDB5727).copy(alpha = 0.5f),
                    Theme.v2.colors.border.light,
                ),
                startY = 0f,
                endY = 400f
            )
        )

        TierType.SILVER -> TierStyle(
            icon = R.drawable.tier_silver,
            titleText = stringResource(R.string.vault_tier_silver),
            discountText = stringResource(R.string.vault_tier_silver_discount),
            amountText = formatVultAmount(5000),
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFC9D6E8).copy(alpha = 0.5f),
                    Theme.v2.colors.border.light,
                ),
                startY = 0f,
                endY = 400f
            )
        )

        TierType.GOLD -> TierStyle(
            icon = R.drawable.tier_gold,
            titleText = stringResource(R.string.vault_tier_gold),
            discountText = stringResource(R.string.vault_tier_gold_discount),
            amountText = formatVultAmount(10000),
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFC25C).copy(alpha = 0.5f),
                    Theme.v2.colors.border.light,
                ),
                startY = 0f,
                endY = 400f
            )
        )

        TierType.PLATINIUM -> TierStyle(
            icon = R.drawable.tier_platinium,
            titleText = stringResource(R.string.vault_tier_platinum),
            discountText = stringResource(R.string.vault_tier_platinum_discount),
            amountText = formatVultAmount(50000),
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF33E6BF).copy(alpha = 0.5f),
                    Theme.v2.colors.border.normal,
                ),
                startY = 0f,
                endY = 400f
            )
        )
    }
}

internal data class TierStyle(
    val icon: Int,
    val titleText: String,
    val discountText: String,
    val amountText: String,
    val gradient: Brush
)


internal enum class TierType { BRONZE, SILVER, GOLD, PLATINIUM }

private fun formatVultAmount(vultAmount: Int): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
    val formattedVult = numberFormat.format(vultAmount)
    val formattedUsd = numberFormat.format(vultAmount)

    return "$formattedVult \$VULT (~\$$formattedUsd)"
}