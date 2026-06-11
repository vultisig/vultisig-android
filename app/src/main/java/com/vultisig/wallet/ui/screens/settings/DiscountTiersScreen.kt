package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.BRONZE_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.DIAMOND_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.GOLD_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.PLATINUM_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.ULTIMATE_DISCOUNT_BPS
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink.TierDiscountBottomSheet
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import com.vultisig.wallet.ui.utils.VsUriHandler
import java.text.NumberFormat
import java.util.Locale

private val BannerGradientStart = Color(0xFF0F1C3E)
private val BannerHeight = 170.dp
private val BannerCardHeight = 139.dp
private val BannerCoinsHeight = 178.dp

@Composable
internal fun DiscountTiersScreen(model: DiscountTiersViewModel = hiltViewModel()) {
    val uriHandler = VsUriHandler()
    val state by model.state.collectAsState()

    V2Scaffold(
        title = stringResource(R.string.vault_settings_discounts),
        onBackClick = model::back,
        actions = {
            VsCircleButton(
                icon = R.drawable.settings_globe,
                onClick = { uriHandler.openUri(VsAuxiliaryLinks.VULT_TOKEN_DOCS) },
                type = VsCircleButtonType.Secondary,
                designType = DesignType.Shined,
                size = VsCircleButtonSize.Small,
                hasBorder = false,
            )
        },
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            TierBanner()

            UiSpacer(size = 24.dp)

            Text(
                text = stringResource(R.string.vault_tier_description),
                style = Theme.brockmann.body.s.regular,
                color = Theme.v2.colors.text.primary,
            )

            UiSpacer(size = 16.dp)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TierType.entries.forEach { tier ->
                    val isActive = state.activeTier == tier
                    TierCard(
                        tierType = tier,
                        isActive = isActive,
                        onClick = { if (!isActive) model.onTierUnlockClick(tier) },
                    )
                }
            }

            UiSpacer(size = 16.dp)
        }
    }

    if (state.showBottomSheetDialog && state.tierClicked != null) {
        TierDiscountBottomSheet(
            tier = state.tierClicked!!,
            onContinue = {
                model.dismissBottomSheet()
                model.navigateToSwaps()
            },
            onDismissRequest = { model.dismissBottomSheet() },
        )
    }
}

@Composable
private fun TierBanner() {
    // The medallion stack is taller than the gradient card and anchored to the bottom, so the
    // top globe coin overflows above the card's top edge (as in Figma). The outer box reserves
    // that overhang in its height; clipToBounds crops only the lowest coin at the banner bottom.
    Box(modifier = Modifier.fillMaxWidth().height(BannerHeight).clipToBounds()) {
        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(BannerCardHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BannerGradientStart, Theme.v2.colors.primary.accent1)
                        )
                    )
        ) {
            Column(
                modifier =
                    Modifier.align(Alignment.CenterStart).padding(start = 24.dp, end = 150.dp)
            ) {
                Text(
                    text = stringResource(R.string.vault_tier_banner_title),
                    style = Theme.brockmann.headings.title1,
                    color = Color.White,
                )

                UiSpacer(size = 4.dp)

                Text(
                    text = stringResource(R.string.vault_tier_banner_subtitle),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.primary,
                )
            }
        }

        Image(
            painter = painterResource(id = R.drawable.tiers_header_coins),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .height(BannerCoinsHeight)
                    .aspectRatio(169f / 167f),
        )
    }
}

@Composable
private fun TierCard(tierType: TierType, isActive: Boolean, onClick: () -> Unit) {
    val style = getStyleByTier(tierType)
    val shape =
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 20.dp, bottomEnd = 20.dp)

    if (isActive) {
        ActiveTierCard(style = style, shape = shape)
    } else {
        CollapsedTierCard(style = style, shape = shape, onClick = onClick)
    }
}

@Composable
private fun CollapsedTierCard(style: TierStyle, shape: Shape, onClick: () -> Unit) {
    TierHeaderRow(
        style = style,
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(Theme.v2.colors.backgrounds.surface1)
                .border(
                    width = 1.dp,
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Theme.v2.colors.border.light, style.accentColor)
                        ),
                    shape = shape,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 24.dp),
    )
}

@Composable
private fun ActiveTierCard(style: TierStyle, shape: Shape) {
    Column(modifier = Modifier.fillMaxWidth().clip(shape)) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(Theme.v2.colors.backgrounds.surface1)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TierHeaderRow(style = style, modifier = Modifier.fillMaxWidth())

            Box(
                modifier =
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(Theme.v2.colors.backgrounds.surface1)
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.vault_tier_swap_discount, style.discountBps),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.primary,
                )
            }

            Text(
                text = stringResource(R.string.vault_tier_more_coming_soon),
                style = Theme.brockmann.body.xs.medium,
                color = Theme.v2.colors.text.tertiary,
            )
        }

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        // Figma: darker tier shade at the top fading to the bright accent at the
                        // bottom (gold #997437 -> #ffc25c == accent*0.6 -> accent).
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    lerp(style.accentColor, Color.Black, 0.4f),
                                    style.accentColor,
                                )
                        )
                    )
                    .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(drawableResId = R.drawable.ic_check, size = 20.dp, tint = Color.White)

            UiSpacer(size = 5.dp)

            Text(
                text = stringResource(R.string.vault_tier_active),
                style = Theme.brockmann.button.semibold.medium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun TierHeaderRow(style: TierStyle, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = style.icon),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )

        UiSpacer(size = 12.dp)

        Text(
            text = style.titleText,
            style = Theme.brockmann.headings.subtitle,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        UiSpacer(size = 12.dp)

        Text(
            text = style.amountText,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
        )
    }
}

@Composable
internal fun getStyleByTier(type: TierType): TierStyle =
    when (type) {
        TierType.BRONZE ->
            TierStyle(
                icon = R.drawable.tier_bronze,
                titleText = stringResource(R.string.vault_tier_bronze),
                amountText = formatVultAmount(1500),
                accentColor = Color(0xFFFF6333),
                discountBps = BRONZE_DISCOUNT_BPS,
            )

        TierType.SILVER ->
            TierStyle(
                icon = R.drawable.tier_silver,
                titleText = stringResource(R.string.vault_tier_silver),
                amountText = formatVultAmount(3000),
                accentColor = Color(0xFFC9D6E8),
                discountBps = SILVER_DISCOUNT_BPS,
            )

        TierType.GOLD ->
            TierStyle(
                icon = R.drawable.tier_gold,
                titleText = stringResource(R.string.vault_tier_gold),
                amountText = formatVultAmount(7500),
                accentColor = Color(0xFFFFC25C),
                discountBps = GOLD_DISCOUNT_BPS,
            )

        TierType.PLATINUM ->
            TierStyle(
                icon = R.drawable.tier_platinum,
                titleText = stringResource(R.string.vault_tier_platinum),
                amountText = formatVultAmount(15000),
                accentColor = Color(0xFF33E6BF),
                discountBps = PLATINUM_DISCOUNT_BPS,
            )

        TierType.DIAMOND ->
            TierStyle(
                icon = R.drawable.tier_diamond,
                titleText = stringResource(R.string.vault_tier_diamond),
                amountText = formatVultAmount(100000),
                accentColor = Color(0xFF9747FF),
                discountBps = DIAMOND_DISCOUNT_BPS,
            )

        TierType.ULTIMATE ->
            TierStyle(
                icon = R.drawable.tier_ultimate,
                titleText = stringResource(R.string.vault_tier_ultimate),
                amountText = formatVultAmount(1000000),
                accentColor = Color(0xFFE5B567),
                discountBps = ULTIMATE_DISCOUNT_BPS,
            )
    }

internal data class TierStyle(
    val icon: Int,
    val titleText: String,
    val amountText: String,
    val accentColor: Color,
    val discountBps: Int,
)

internal enum class TierType {
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM,
    DIAMOND,
    ULTIMATE,
}

internal fun TierType?.applyExtraDiscount(hasNFT: Boolean): TierType? {
    if (!hasNFT) {
        return this
    }

    return when {
        this == null -> TierType.BRONZE
        this == TierType.SILVER -> TierType.GOLD
        this == TierType.GOLD -> TierType.PLATINUM
        // starting from PLATINUM NFT has no effect
        this == TierType.PLATINUM -> TierType.PLATINUM
        this == TierType.DIAMOND -> TierType.DIAMOND
        this == TierType.ULTIMATE -> TierType.ULTIMATE
        else -> null
    }
}

private fun formatVultAmount(vultAmount: Int): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
    val formattedVult = numberFormat.format(vultAmount)

    return "$formattedVult \$VULT"
}

@Preview
@Composable
internal fun DiscountTiersScreenPreview() {
    Column(
        modifier =
            Modifier.fillMaxWidth().background(Theme.v2.colors.backgrounds.primary).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TierBanner()
        TierType.entries.forEach { tier ->
            TierCard(tierType = tier, isActive = tier == TierType.GOLD, onClick = {})
        }
    }
}

@Preview
@Composable
internal fun DiscountTiersScreenPreview2() {
    Column(
        modifier =
            Modifier.fillMaxWidth().background(Theme.v2.colors.backgrounds.primary).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TierType.entries.forEach { tier ->
            TierCard(tierType = tier, isActive = tier == TierType.GOLD, onClick = {})
        }
    }
}
