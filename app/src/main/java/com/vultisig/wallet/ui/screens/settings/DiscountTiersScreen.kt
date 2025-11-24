package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.topbar.V2Topbar
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink.TierDiscountBottomSheet
import com.vultisig.wallet.ui.screens.v2.components.VsButton
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import com.vultisig.wallet.ui.utils.VsUriHandler
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun DiscountTiersScreen(
    navController: NavHostController,
    vaultId: String,
    model: DiscountTiersViewModel = hiltViewModel(),
) {
    val uriHandler = VsUriHandler()
    val state by model.state.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.backgrounds.secondary),
        topBar = {
            V2Topbar(
                title = stringResource(R.string.vault_settings_discounts),
                onBackClick = { navController.popBackStack() },
                actions = {
                    VsCircleButton(
                        icon = R.drawable.settings_globe,
                        onClick = {
                            uriHandler.openUri(VsAuxiliaryLinks.VULT_TOKEN_DOCS)
                        },
                        type = VsCircleButtonType.Secondary,
                        designType = DesignType.Shined,
                        size = VsCircleButtonSize.Small,
                        hasBorder = false,
                    )
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .padding(horizontal = 16.dp, vertical = 16.dp)
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
                text = stringResource(R.string.vault_tier_description),
                style = Theme.brockmann.body.s.regular,
                textAlign = TextAlign.Start,
            )

            UiSpacer(size = 16.dp)

            TierCard(
                tierType = TierType.BRONZE,
                isActive = state.activeTier == TierType.BRONZE,
                isExpanded = state.expandedTiers.contains(TierType.BRONZE),
                onClickUnlock = {
                    model.onTierUnlockClick(TierType.BRONZE)
                },
                onClickCard = {
                    model.expandOrCollapseTierInfo(TierType.BRONZE)
                }
            )

            TierCard(
                tierType = TierType.SILVER,
                isActive = state.activeTier == TierType.SILVER,
                isExpanded = state.expandedTiers.contains(TierType.SILVER),
                onClickUnlock = {
                    model.onTierUnlockClick(TierType.SILVER)
                },
                onClickCard = {
                    model.expandOrCollapseTierInfo(TierType.SILVER)
                }
            )

            TierCard(
                tierType = TierType.GOLD,
                isActive = state.activeTier == TierType.GOLD,
                isExpanded = state.expandedTiers.contains(TierType.GOLD),
                onClickUnlock = {
                    model.onTierUnlockClick(TierType.GOLD)
                },
                onClickCard = {
                    model.expandOrCollapseTierInfo(TierType.GOLD)
                }
            )

            TierCard(
                tierType = TierType.PLATINUM,
                isActive = state.activeTier == TierType.PLATINUM,
                isExpanded = state.expandedTiers.contains(TierType.PLATINUM),
                onClickUnlock = {
                    model.onTierUnlockClick(TierType.PLATINUM)
                },
                onClickCard = {
                    model.expandOrCollapseTierInfo(TierType.PLATINUM)
                }
            )

            TierCard(
                tierType = TierType.DIAMOND,
                isActive = state.activeTier == TierType.DIAMOND,
                isExpanded = state.expandedTiers.contains(TierType.DIAMOND),
                onClickUnlock = {
                    model.onTierUnlockClick(TierType.DIAMOND)
                },
                onClickCard = {
                    model.expandOrCollapseTierInfo(TierType.DIAMOND)
                }
            )

            TierCard(
                tierType = TierType.ULTIMATE,
                isActive = state.activeTier == TierType.ULTIMATE,
                isExpanded = state.expandedTiers.contains(TierType.ULTIMATE),
                onClickUnlock = {
                    model.onTierUnlockClick(TierType.ULTIMATE)
                },
                onClickCard = {
                    model.expandOrCollapseTierInfo(TierType.ULTIMATE)
                }
            )

            UiSpacer(size = 16.dp)
        }
    }

    if (state.showBottomSheetDialog && state.tierClicked != null) {
        TierDiscountBottomSheet(
            tier = state.tierClicked!!,
            onContinue = {
                model.dismissBottomSheet()
                model.navigateToSwaps(navController, vaultId)
            },
            onDismissRequest = {
                model.dismissBottomSheet()
            }
        )
    }
}

@Composable
private fun TierCard(
    tierType: TierType,
    isActive: Boolean = false,
    isExpanded: Boolean = false,
    onClickUnlock: () -> Unit,
    onClickCard: () -> Unit,
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
            .clickable { onClickCard.invoke() }
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
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(styleTier.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = (-4).dp) // compensate internal image padding
                            .size(55.dp)
                            .padding(start = 0.dp, end = 6.dp, bottom = 6.dp, top = 6.dp)
                    )

                    Text(
                        text = styleTier.titleText,
                        style = Theme.brockmann.headings.title1,
                        color = Theme.v2.colors.text.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                UiSpacer(size = 6.dp)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.normal,
                            shape = RoundedCornerShape(50)
                        )
                        .background(Theme.v2.colors.backgrounds.surface2)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = styleTier.discountText,
                        style = Theme.brockmann.supplementary.footnote,
                        color = Theme.v2.colors.text.primary,
                        maxLines = 1,
                    )
                }
            }

            if (isExpanded) {
                UiSpacer(size = 8.dp)

                Text(
                    text = stringResource(R.string.vault_tier_hold),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.extraLight
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = styleTier.amountText,
                        style = Theme.brockmann.body.l.regular,
                        color = Theme.v2.colors.text.primary
                    )

                    if (isActive) {
                        UiSpacer(1f)

                        UiIcon(
                            drawableResId = R.drawable.ic_check,
                            tint = Theme.v2.colors.alerts.success,
                            size = 18.dp,
                            modifier = Modifier.padding(4.dp),
                        )

                        Text(
                            text = stringResource(R.string.vault_tier_active),
                            style = Theme.brockmann.supplementary.footnote,
                            color = Theme.v2.colors.alerts.success,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }

                UiSpacer(size = 16.dp)

                VsButton(
                    label = stringResource(R.string.vault_tier_unlock),
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = onClickUnlock,
                )
            }
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
            amountText = formatVultAmount(1500),
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
            amountText = formatVultAmount(3000),
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
            amountText = formatVultAmount(75000),
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFC25C).copy(alpha = 0.5f),
                    Theme.v2.colors.border.light,
                ),
                startY = 0f,
                endY = 400f
            )
        )

        TierType.PLATINUM -> TierStyle(
            icon = R.drawable.tier_platinum,
            titleText = stringResource(R.string.vault_tier_platinum),
            discountText = stringResource(R.string.vault_tier_platinum_discount),
            amountText = formatVultAmount(15000),
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF33E6BF).copy(alpha = 0.5f),
                    Theme.v2.colors.border.normal,
                ),
                startY = 0f,
                endY = 400f
            )
        )

        TierType.DIAMOND -> TierStyle(
            icon = R.drawable.tier_platinum,
            titleText = stringResource(R.string.vault_tier_diamond),
            discountText = stringResource(R.string.vault_tier_diamond_discount),
            amountText = formatVultAmount(100000),
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF9747FF).copy(alpha = 0.5f),
                    Theme.v2.colors.border.normal,
                ),
                startY = 0f,
                endY = 400f
            )
        )

        TierType.ULTIMATE -> TierStyle(
            icon = R.drawable.tier_platinum,
            titleText = stringResource(R.string.vault_tier_ultimate),
            discountText = stringResource(R.string.vault_tier_ultimate_discount),
            amountText = formatVultAmount(1000000),
            gradient = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFC700), // yellow
                    Color(0xFFFF6B00), // orange
                    Color(0xFFFF00E5), // magenta
                    Color(0xFF6A00FF), // purple
                    Color(0xFF00C2FF), // cyan
                    Color(0xFF00FF85), // green
                ),
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


internal enum class TierType { BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, ULTIMATE }

private fun formatVultAmount(vultAmount: Int): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
    val formattedVult = numberFormat.format(vultAmount)

    return "$formattedVult \$VULT"
}

@Preview(showBackground = true)
@Composable
private fun DiscountTiersScreenPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TierCard(tierType = TierType.BRONZE, onClickUnlock = {}, onClickCard = {})
            TierCard(tierType = TierType.SILVER, onClickUnlock = {}, onClickCard = {})
            TierCard(tierType = TierType.GOLD, onClickUnlock = {}, onClickCard = {})
            TierCard(tierType = TierType.PLATINUM, onClickUnlock = {}, onClickCard = {})
            TierCard(tierType = TierType.DIAMOND, onClickUnlock = {}, onClickCard = {})
            TierCard(tierType = TierType.ULTIMATE, onClickUnlock = {}, onClickCard = {})
        }
    }
}