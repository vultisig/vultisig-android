package com.vultisig.wallet.ui.screens.swap.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.models.swap.LimitExpiryOption
import com.vultisig.wallet.ui.models.swap.LimitOrderUiModel
import com.vultisig.wallet.ui.models.swap.LimitPricePreset
import com.vultisig.wallet.ui.models.swap.LimitPriceUnit
import com.vultisig.wallet.ui.theme.Theme

/**
 * The THORChain limit-order ("Execute when") form body — the content shown under the Limit tab.
 * Renders the target-price entry with a $/asset unit toggle, Market/+1/+5/+10% presets, an expiry
 * selector, a price warning, and a collapsed asset summary. Matches Figma node 78798:74520.
 *
 * Purely presentational: all values come from [state]; interactions are surfaced as callbacks.
 */
@Composable
internal fun LimitSwapForm(
    state: LimitOrderUiModel,
    onPresetClick: (LimitPricePreset) -> Unit,
    onExpiryClick: (LimitExpiryOption) -> Unit,
    onToggleUnit: (LimitPriceUnit) -> Unit,
    onEditAssets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExecuteWhenCard(
            state = state,
            onPresetClick = onPresetClick,
            onExpiryClick = onExpiryClick,
            onToggleUnit = onToggleUnit,
        )
        AssetSummaryRow(state = state, onEditAssets = onEditAssets)
    }
}

@Composable
private fun ExecuteWhenCard(
    state: LimitOrderUiModel,
    onPresetClick: (LimitPricePreset) -> Unit,
    onExpiryClick: (LimitExpiryOption) -> Unit,
    onToggleUnit: (LimitPriceUnit) -> Unit,
) {
    val colors = Theme.v2.colors
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.backgrounds.background)
                .border(1.dp, colors.border.light, RoundedCornerShape(24.dp))
                .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.limit_swap_execute_when),
            style = Theme.brockmann.body.m.medium,
            color = colors.text.primary,
        )
        HorizontalDivider(thickness = 1.dp, color = colors.border.light)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                PriceEntryBlock(state = state, onPresetClick = onPresetClick)
                PriceUnitToggle(
                    unit = state.priceUnit,
                    onToggleUnit = onToggleUnit,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp),
                )
            }
            ExpiryRow(selected = state.selectedExpiry, onExpiryClick = onExpiryClick)
        }

        state.warningRes?.let { warningRes ->
            Text(
                text = stringResource(warningRes),
                style = Theme.brockmann.supplementary.caption,
                color = colors.alerts.warning,
            )
        }
    }
}

@Composable
private fun PriceEntryBlock(state: LimitOrderUiModel, onPresetClick: (LimitPricePreset) -> Unit) {
    val colors = Theme.v2.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.limit_swap_market_label),
                    style = Theme.brockmann.supplementary.caption,
                    color = colors.text.secondary,
                )
                Text(
                    text = state.marketPriceLabel,
                    style = Theme.brockmann.supplementary.caption,
                    color = colors.text.secondary,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                state.referenceLogo?.let { logo ->
                    TokenLogo(
                        logo = logo,
                        title = state.buyTicker,
                        modifier = Modifier.size(24.dp),
                        errorLogoModifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = state.referenceAmountLabel,
                    style = Theme.brockmann.supplementary.caption,
                    color = colors.text.secondary,
                )
            }

            Text(
                text = state.priceText,
                style = Theme.brockmann.headings.largeTitle,
                color = colors.text.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.secondaryPriceLabel,
                style = Theme.brockmann.body.m.medium,
                color = colors.text.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            state.percentFromMarketLabel?.let { percent ->
                Text(
                    text = percent,
                    style = Theme.brockmann.supplementary.caption,
                    color = colors.text.tertiary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LimitPricePreset.entries.forEach { preset ->
                LimitPill(
                    text = stringResource(preset.labelRes),
                    selected = state.selectedPreset == preset,
                    onClick = { onPresetClick(preset) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ExpiryRow(selected: LimitExpiryOption, onExpiryClick: (LimitExpiryOption) -> Unit) {
    val colors = Theme.v2.colors
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.backgrounds.disabled)
                .border(1.dp, colors.border.light, RoundedCornerShape(16.dp))
                .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.limit_swap_expiry_label),
            style = Theme.brockmann.body.m.medium,
            color = colors.text.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LimitExpiryOption.entries.forEach { option ->
                LimitPill(
                    text = stringResource(option.labelRes),
                    selected = selected == option,
                    onClick = { onExpiryClick(option) },
                )
            }
        }
    }
}

@Composable
private fun LimitPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Theme.v2.colors
    val borderColor = if (selected) colors.border.normal else colors.border.light
    val background = if (selected) colors.backgrounds.surface1 else Color.Transparent
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(100.dp))
                .background(background)
                .border(1.dp, borderColor, RoundedCornerShape(100.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Theme.brockmann.supplementary.caption,
            color = if (selected) colors.text.primary else colors.text.secondary,
        )
    }
}

@Composable
private fun PriceUnitToggle(
    unit: LimitPriceUnit,
    onToggleUnit: (LimitPriceUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Theme.v2.colors
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(colors.backgrounds.surface1)
                .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        UnitToggleButton(
            icon = R.drawable.ic_coins,
            selected = unit == LimitPriceUnit.Asset,
            onClick = { onToggleUnit(LimitPriceUnit.Asset) },
        )
        UnitToggleButton(
            icon = R.drawable.ic_dollar_sign,
            selected = unit == LimitPriceUnit.Fiat,
            onClick = { onToggleUnit(LimitPriceUnit.Fiat) },
        )
    }
}

@Composable
private fun UnitToggleButton(icon: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = Theme.v2.colors
    Box(
        modifier =
            Modifier.size(32.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) colors.primary.accent3 else Color.Transparent)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (selected) colors.text.primary else colors.text.secondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AssetSummaryRow(state: LimitOrderUiModel, onEditAssets: () -> Unit) {
    val colors = Theme.v2.colors
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.backgrounds.background)
                .border(1.dp, colors.border.light, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.limit_swap_asset_label),
            style = Theme.brockmann.body.m.medium,
            color = colors.text.primary,
        )
        AssetSummaryLeg(
            label = stringResource(R.string.limit_swap_sell),
            ticker = state.sellTicker,
            logo = state.sellLogo,
            modifier = Modifier.weight(1f),
        )
        AssetSummaryLeg(
            label = stringResource(R.string.limit_swap_buy),
            ticker = state.buyTicker,
            logo = state.buyLogo,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.check_2),
            contentDescription = null,
            tint = colors.alerts.success,
            modifier = Modifier.size(16.dp),
        )
        Icon(
            painter = painterResource(R.drawable.pen_v2),
            contentDescription = null,
            tint = colors.text.primary,
            modifier = Modifier.size(16.dp).clickable(onClick = onEditAssets),
        )
    }
}

@Composable
private fun AssetSummaryLeg(
    label: String,
    ticker: String,
    logo: ImageModel?,
    modifier: Modifier = Modifier,
) {
    val colors = Theme.v2.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Theme.brockmann.supplementary.caption,
            color = colors.text.tertiary,
        )
        logo?.let {
            TokenLogo(
                logo = it,
                title = ticker,
                modifier = Modifier.size(16.dp),
                errorLogoModifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = ticker,
            style = Theme.brockmann.supplementary.caption,
            color = colors.text.secondary,
        )
    }
}
