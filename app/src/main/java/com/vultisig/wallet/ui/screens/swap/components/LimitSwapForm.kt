package com.vultisig.wallet.ui.screens.swap.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.inputs.VsBasicTextField
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel
import com.vultisig.wallet.ui.models.swap.LimitExpiryOption
import com.vultisig.wallet.ui.models.swap.LimitFormSection
import com.vultisig.wallet.ui.models.swap.LimitOrderUiModel
import com.vultisig.wallet.ui.models.swap.LimitPricePreset
import com.vultisig.wallet.ui.models.swap.LimitPriceUnit
import com.vultisig.wallet.ui.theme.Theme

/** Height of the target-price frame in Figma; the reference/price/secondary trio centers in it. */
private val PRICE_BLOCK_HEIGHT = 211.dp

/**
 * The THORChain limit-order ("Execute when") form body — the content shown under the Limit tab.
 * Renders the target-price entry with a $/asset unit toggle, Market/+1/+5/+10% presets, an expiry
 * selector, a price warning, and the asset section. Matches Figma node 78798:74520.
 *
 * The two sections behave as an accordion: exactly one of "Execute when" and "Asset" is expanded at
 * a time ([expandedSection]), the other collapses to a summary row whose pencil expands it again.
 * The expanded asset editor is Figma node 78798:74351 and hosts the same sell/buy token inputs the
 * Market tab uses.
 *
 * Purely presentational: all values come from [state]; interactions are surfaced as callbacks.
 */
@Composable
internal fun LimitSwapForm(
    state: LimitOrderUiModel,
    srcToken: TokenBalanceUiModel?,
    dstToken: TokenBalanceUiModel?,
    srcFiatValue: String,
    srcAmountTextFieldState: TextFieldState,
    expandedSection: LimitFormSection,
    onPresetClick: (LimitPricePreset) -> Unit,
    onExpiryClick: (LimitExpiryOption) -> Unit,
    onToggleUnit: (LimitPriceUnit) -> Unit,
    onExpandSection: (LimitFormSection) -> Unit,
    onSelectSrcNetworkClick: () -> Unit,
    onSelectSrcTokenClick: () -> Unit,
    onSelectDstNetworkClick: () -> Unit,
    onSelectDstTokenClick: () -> Unit,
    onFlipSelectedTokens: () -> Unit,
    modifier: Modifier = Modifier,
    srcAmountInteractionSource: MutableInteractionSource? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (expandedSection == LimitFormSection.ExecuteWhen) {
            ExecuteWhenCard(
                state = state,
                onPresetClick = onPresetClick,
                onExpiryClick = onExpiryClick,
                onToggleUnit = onToggleUnit,
            )
        } else {
            ExecuteWhenSummaryRow(
                state = state,
                onEdit = { onExpandSection(LimitFormSection.ExecuteWhen) },
            )
        }

        if (expandedSection == LimitFormSection.Asset) {
            AssetEditorCard(
                state = state,
                srcToken = srcToken,
                dstToken = dstToken,
                srcFiatValue = srcFiatValue,
                srcAmountTextFieldState = srcAmountTextFieldState,
                srcAmountInteractionSource = srcAmountInteractionSource,
                onSelectSrcNetworkClick = onSelectSrcNetworkClick,
                onSelectSrcTokenClick = onSelectSrcTokenClick,
                onSelectDstNetworkClick = onSelectDstNetworkClick,
                onSelectDstTokenClick = onSelectDstTokenClick,
                onFlipSelectedTokens = onFlipSelectedTokens,
            )
        } else {
            AssetSummaryRow(
                state = state,
                onEditAssets = { onExpandSection(LimitFormSection.Asset) },
            )
        }
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
            PriceEntryBlock(
                state = state,
                onPresetClick = onPresetClick,
                onToggleUnit = onToggleUnit,
            )
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
private fun PriceEntryBlock(
    state: LimitOrderUiModel,
    onPresetClick: (LimitPricePreset) -> Unit,
    onToggleUnit: (LimitPriceUnit) -> Unit,
) {
    val colors = Theme.v2.colors
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Fixed-height, vertically centered stack: Figma gives this block a 211dp frame so the
        // reference/price/secondary trio floats in the middle of the card with breathing room
        // above and below, rather than hugging the divider. The unit toggle rides the same frame's
        // centre so it stays level with the price instead of drifting up to the divider.
        Box(modifier = Modifier.fillMaxWidth().height(PRICE_BLOCK_HEIGHT)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
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
            }
            PriceUnitToggle(
                unit = state.priceUnit,
                onToggleUnit = onToggleUnit,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            )
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
        val toggleDescription = stringResource(R.string.limit_swap_toggle_price_unit)
        UnitToggleButton(
            icon = R.drawable.ic_coins,
            selected = unit == LimitPriceUnit.Asset,
            contentDescription = toggleDescription,
            onClick = { onToggleUnit(LimitPriceUnit.Asset) },
        )
        UnitToggleButton(
            icon = R.drawable.ic_dollar_sign,
            selected = unit == LimitPriceUnit.Fiat,
            contentDescription = toggleDescription,
            onClick = { onToggleUnit(LimitPriceUnit.Fiat) },
        )
    }
}

@Composable
private fun UnitToggleButton(
    icon: Int,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
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
            contentDescription = contentDescription,
            tint = if (selected) colors.text.primary else colors.text.secondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Expanded asset editor (Figma node 78798:74351): the "Asset" card hosting the same sell/buy token
 * inputs and flip button the Market tab uses, so the pair and sell amount can be edited without
 * leaving the Limit form. Collapses back to [AssetSummaryRow] when "Execute when" is expanded.
 */
@Composable
private fun AssetEditorCard(
    state: LimitOrderUiModel,
    srcToken: TokenBalanceUiModel?,
    dstToken: TokenBalanceUiModel?,
    srcFiatValue: String,
    srcAmountTextFieldState: TextFieldState,
    srcAmountInteractionSource: MutableInteractionSource?,
    onSelectSrcNetworkClick: () -> Unit,
    onSelectSrcTokenClick: () -> Unit,
    onSelectDstNetworkClick: () -> Unit,
    onSelectDstTokenClick: () -> Unit,
    onFlipSelectedTokens: () -> Unit,
) {
    val colors = Theme.v2.colors
    val space = 8.dp
    var topCenter by remember { mutableStateOf(Offset.Zero) }
    var bottomCenter by remember { mutableStateOf(Offset.Zero) }

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
            text = stringResource(R.string.limit_swap_asset_label),
            style = Theme.brockmann.body.m.medium,
            color = colors.text.primary,
        )
        HorizontalDivider(thickness = 1.dp, color = colors.border.light)

        Column(verticalArrangement = Arrangement.spacedBy(space)) {
            Box {
                SrcTokenInput(
                    isLoading = false,
                    title = stringResource(R.string.limit_swap_sell),
                    selectedToken = srcToken,
                    fiatValue = srcFiatValue,
                    space = space,
                    onSelectNetworkClick = onSelectSrcNetworkClick,
                    onSelectTokenClick = onSelectSrcTokenClick,
                    onCircleBoundsChanged = { topCenter = it },
                    textFieldContent = {
                        VsBasicTextField(
                            textFieldState = srcAmountTextFieldState,
                            style = Theme.brockmann.headings.title2,
                            color = colors.text.primary,
                            textAlign = TextAlign.End,
                            hint = "0",
                            hintColor = colors.text.tertiary,
                            hintStyle = Theme.brockmann.headings.title2,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            interactionSource = srcAmountInteractionSource,
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                            modifier = Modifier.fillMaxWidth().testTag("LimitSwapForm.sellAmount"),
                        )
                    },
                )
                SwapTokenFlipButton(
                    isLoading = false,
                    hasError = false,
                    topCenter = topCenter,
                    bottomCenter = bottomCenter,
                    space = space,
                    onFlip = onFlipSelectedTokens,
                    onBoundsChanged = {},
                )
            }

            DstTokenInput(
                isLoading = false,
                title = stringResource(R.string.limit_swap_buy),
                selectedToken = dstToken,
                // The buy leg is priced by the target, not by a live quote, so it mirrors the sell
                // leg's fiat value: at the limit price both legs are worth the same.
                fiatValue = srcFiatValue,
                space = space,
                onSelectNetworkClick = onSelectDstNetworkClick,
                onSelectTokenClick = onSelectDstTokenClick,
                onCircleBoundsChanged = { bottomCenter = it },
                textFieldContent = {
                    Text(
                        text = state.buyAmountText,
                        style = Theme.brockmann.headings.title2,
                        color = colors.text.primary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun AssetSummaryRow(state: LimitOrderUiModel, onEditAssets: () -> Unit) {
    SummaryRow(
        title = stringResource(R.string.limit_swap_asset_label),
        editContentDescription = stringResource(R.string.limit_swap_edit_assets),
        onEdit = onEditAssets,
    ) {
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
    }
}

/**
 * Collapsed counterpart of [ExecuteWhenCard], shown while the asset editor is expanded: the target
 * price and the chosen expiry, with a pencil that expands the price entry again.
 */
@Composable
private fun ExecuteWhenSummaryRow(state: LimitOrderUiModel, onEdit: () -> Unit) {
    val colors = Theme.v2.colors
    SummaryRow(
        title = stringResource(R.string.limit_swap_execute_when),
        editContentDescription = stringResource(R.string.limit_swap_edit_price),
        onEdit = onEdit,
    ) {
        Text(
            text = state.priceText,
            style = Theme.brockmann.supplementary.caption,
            color = colors.text.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(state.selectedExpiry.labelRes),
            style = Theme.brockmann.supplementary.caption,
            color = colors.text.tertiary,
        )
    }
}

/**
 * Shared chrome for the two collapsed sections: the card, its title, the summary [content], the
 * green readiness check, and the pencil that expands the section.
 */
@Composable
private fun SummaryRow(
    title: String,
    editContentDescription: String,
    onEdit: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = Theme.v2.colors
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.backgrounds.background)
                .border(1.dp, colors.border.light, RoundedCornerShape(24.dp))
                .clickable(onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = Theme.brockmann.body.m.medium, color = colors.text.primary)
        content()
        Icon(
            painter = painterResource(R.drawable.check_2),
            contentDescription = null,
            tint = colors.alerts.success,
            modifier = Modifier.size(16.dp),
        )
        Box(
            modifier =
                Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.pen_v2),
                contentDescription = editContentDescription,
                tint = colors.text.primary,
                modifier = Modifier.size(16.dp),
            )
        }
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
