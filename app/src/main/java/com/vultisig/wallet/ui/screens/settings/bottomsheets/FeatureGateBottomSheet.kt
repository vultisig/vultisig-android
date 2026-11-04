package com.vultisig.wallet.ui.screens.settings.bottomsheets

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.screens.settings.getStyleByTier
import com.vultisig.wallet.ui.theme.Theme

internal enum class FeatureGateSheetStyle {
    Default,
    Settings,
}

private val SETTINGS_GATE_TOP_PADDING = 23.dp
private val SETTINGS_GATE_BOTTOM_PADDING = 15.dp
private val SETTINGS_GATE_TEXT_INSET = 29.dp
private val SETTINGS_CARD_SHAPE =
    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
private val SETTINGS_BALANCE_SHAPE = RoundedCornerShape(14.dp)
private val SETTINGS_FOOTER_SHAPE = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
private val SETTINGS_FOOTER_OVERLAP = 22.dp
private val SETTINGS_FOOTER_VISIBLE_HEIGHT = 52.dp
private val SETTINGS_NOTE_LINE_HEIGHT = 18.dp

/**
 * Reusable feature-gate popup shown when a user taps a feature locked behind a $VULT tier (Figma
 * node 77059-110340). It describes the *gated feature*, the tier requirement to unlock it, the
 * vault's current $VULT balance, and a "Get $VULT" action — parameterized so every gate swaps in
 * its own feature and required tier.
 *
 * This is the single source of truth for the locked-feature design; the Advanced Swap upsell
 * (#4858) and the Custom RPC upsell (#4787) both render through it.
 *
 * @param requiredTier tier required to unlock the feature; drives the badge, name, and CTA accent.
 * @param balanceText the vault's current $VULT balance, pre-formatted with its unit (e.g. "2,340
 *   VULT").
 * @param thresholdText the required $VULT holdings, pre-formatted with its unit (e.g. "3,000
 *   VULT").
 * @param isBelowThreshold when true the balance is rendered in the warning accent.
 * @param style [FeatureGateSheetStyle.Settings] is the settings redesign, where [featureIcon] is a
 *   finished medallion; every other gate keeps [FeatureGateSheetStyle.Default].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeatureGateBottomSheet(
    @DrawableRes featureIcon: Int,
    featureTitle: String,
    featureDescription: String,
    requiredTier: TierType,
    balanceText: String,
    thresholdText: String,
    isBelowThreshold: Boolean,
    onGetVult: () -> Unit,
    onDismiss: () -> Unit,
    style: FeatureGateSheetStyle = FeatureGateSheetStyle.Default,
) {
    V2BottomSheet(
        onDismissRequest = onDismiss,
        scrimColor =
            if (style == FeatureGateSheetStyle.Settings)
                Theme.v2.colors.neutrals.n900.copy(alpha = 0.5f)
            else BottomSheetDefaults.ScrimColor,
        leftAction = {
            VsCircleButton(
                onClick = onDismiss,
                drawableResId = R.drawable.big_close,
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Tertiary,
            )
        },
    ) {
        if (style == FeatureGateSheetStyle.Settings) {
            SettingsGateContent(
                featureIcon = featureIcon,
                featureTitle = featureTitle,
                featureDescription = featureDescription,
                requiredTier = requiredTier,
                balanceText = balanceText,
                thresholdText = thresholdText,
                isBelowThreshold = isBelowThreshold,
                onGetVult = onGetVult,
            )
            return@V2BottomSheet
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier.size(56.dp)
                        .clip(CircleShape)
                        .background(Theme.v2.colors.backgrounds.secondary)
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.primary.accent4.copy(alpha = 0.4f),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                UiIcon(
                    drawableResId = featureIcon,
                    size = 24.dp,
                    tint = Theme.v2.colors.primary.accent4,
                )
            }

            UiSpacer(24.dp)

            Text(
                text = featureTitle,
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(12.dp)

            Text(
                text = featureDescription,
                style = Theme.brockmann.body.s.regular,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            UiSpacer(24.dp)

            RequiresCard(
                requiredTier = requiredTier,
                balanceText = balanceText,
                thresholdText = thresholdText,
                isBelowThreshold = isBelowThreshold,
                onGetVult = onGetVult,
            )
        }
    }
}

@Composable
private fun SettingsGateContent(
    @DrawableRes featureIcon: Int,
    featureTitle: String,
    featureDescription: String,
    requiredTier: TierType,
    balanceText: String,
    thresholdText: String,
    isBelowThreshold: Boolean,
    onGetVult: () -> Unit,
) {
    val tierStyle = getStyleByTier(requiredTier)
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = SETTINGS_GATE_TOP_PADDING, bottom = SETTINGS_GATE_BOTTOM_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painter = painterResource(featureIcon), contentDescription = null)
        UiSpacer(32.dp)
        Text(
            text = featureTitle,
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )
        UiSpacer(16.dp)
        Text(
            text = featureDescription,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = SETTINGS_GATE_TEXT_INSET),
        )
        UiSpacer(20.dp)
        SettingsRequiresCard(
            tierIcon = tierStyle.icon,
            tierName = tierStyle.titleText,
            balanceText = balanceText,
            thresholdText = thresholdText,
            isBelowThreshold = isBelowThreshold,
        )
        SettingsGetVultFooter(accentColor = tierStyle.accentColor, onClick = onGetVult)
    }
}

@Composable
private fun SettingsRequiresCard(
    @DrawableRes tierIcon: Int,
    tierName: String,
    balanceText: String,
    thresholdText: String,
    isBelowThreshold: Boolean,
) {
    val noteStyle = Theme.satoshi.price.footnote
    val noteModifier = Modifier.heightIn(min = SETTINGS_NOTE_LINE_HEIGHT)
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .zIndex(1f)
                .clip(SETTINGS_CARD_SHAPE)
                .background(Theme.v2.colors.backgrounds.surface1)
                .border(1.dp, Theme.v2.colors.border.light, SETTINGS_CARD_SHAPE)
                .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.feature_gate_requires),
            style = noteStyle,
            color = Theme.v2.colors.text.tertiary,
            modifier = noteModifier,
        )
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(tierIcon),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Column(
                modifier = Modifier.padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.feature_gate_tier_requirement, tierName),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )
                Text(
                    text = stringResource(R.string.feature_gate_hold_threshold, thresholdText),
                    style = noteStyle,
                    color = Theme.v2.colors.text.tertiary,
                    modifier = noteModifier,
                )
            }
        }
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(SETTINGS_BALANCE_SHAPE)
                    .background(Theme.v2.colors.variables.backgroundsSurface12)
                    .border(1.dp, Theme.v2.colors.border.light, SETTINGS_BALANCE_SHAPE)
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UiIcon(
                drawableResId = R.drawable.wallet_filled,
                size = 16.dp,
                tint = Theme.v2.colors.text.tertiary,
            )
            Text(
                text = stringResource(R.string.feature_gate_your_balance),
                style = noteStyle,
                color = Theme.v2.colors.text.tertiary,
                modifier = noteModifier.weight(1f),
            )
            Text(
                text = balanceText,
                style = noteStyle,
                modifier = noteModifier,
                color =
                    if (isBelowThreshold) Theme.v2.colors.alerts.warning
                    else Theme.v2.colors.text.primary,
            )
        }
    }
}

/** Slides [SETTINGS_FOOTER_OVERLAP] under the card; only the part left showing takes taps. */
@Composable
private fun SettingsGetVultFooter(accentColor: Color, onClick: () -> Unit) {
    val darkEnd = lerp(accentColor, Color.Black, 0.4f)
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val overlap = SETTINGS_FOOTER_OVERLAP.roundToPx()
                    layout(placeable.width, placeable.height - overlap) {
                        placeable.place(0, -overlap)
                    }
                }
                .height(SETTINGS_FOOTER_VISIBLE_HEIGHT + SETTINGS_FOOTER_OVERLAP)
                .clip(SETTINGS_FOOTER_SHAPE)
                .background(
                    Brush.verticalGradient(
                        0.23f to darkEnd,
                        1f to lerp(darkEnd, accentColor, 0.85f),
                    )
                ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(SETTINGS_FOOTER_VISIBLE_HEIGHT)
                    .clickable(onClick = onClick)
                    .padding(bottom = 19.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = stringResource(R.string.feature_gate_get_vult),
                style = Theme.brockmann.button.semibold.medium,
                color = Theme.v2.colors.text.button.primary,
            )
        }
    }
}

@Composable
private fun RequiresCard(
    requiredTier: TierType,
    balanceText: String,
    thresholdText: String,
    isBelowThreshold: Boolean,
    onGetVult: () -> Unit,
) {
    val tierStyle = getStyleByTier(requiredTier)

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(Theme.v2.radius.xl)
                .border(1.dp, Theme.v2.colors.border.light, Theme.v2.radius.xl)
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(Theme.v2.colors.backgrounds.secondary)
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.feature_gate_requires),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(tierStyle.icon),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text =
                            stringResource(
                                R.string.feature_gate_tier_requirement,
                                tierStyle.titleText,
                            ),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                    )
                    Text(
                        text = stringResource(R.string.feature_gate_hold_threshold, thresholdText),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.tertiary,
                    )
                }
            }

            BalanceRow(balanceText = balanceText, isBelowThreshold = isBelowThreshold)
        }

        // Tier-accent "Get $VULT" footer joined to the card's bottom edge (single rounded
        // container). Darker accent at the top fading to the bright tier accent at the bottom.
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                lerp(tierStyle.accentColor, Color.Black, 0.4f),
                                tierStyle.accentColor,
                            )
                        )
                    )
                    .clickable(onClick = onGetVult),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.feature_gate_get_vult),
                style = Theme.brockmann.button.semibold.semibold,
                color = Theme.v2.colors.text.primary,
            )
        }
    }
}

@Composable
private fun BalanceRow(balanceText: String, isBelowThreshold: Boolean) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(Theme.v2.radius.md)
                .background(Theme.v2.colors.backgrounds.tertiary)
                .border(1.dp, Theme.v2.colors.border.light, Theme.v2.radius.md)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UiIcon(
            drawableResId = R.drawable.wallet,
            size = 16.dp,
            tint = Theme.v2.colors.text.tertiary,
        )
        Text(
            text = stringResource(R.string.feature_gate_your_balance),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = balanceText,
            style = Theme.brockmann.supplementary.caption,
            color =
                if (isBelowThreshold) Theme.v2.colors.alerts.warning
                else Theme.v2.colors.text.primary,
        )
    }
}

@Preview
@Composable
private fun FeatureGateBottomSheetPreview() {
    FeatureGateBottomSheet(
        featureIcon = R.drawable.settings_globe,
        featureTitle = "Custom RPC",
        featureDescription =
            "Point Vultisig at your own nodes. Faster queries, higher rate limits, " +
                "and full privacy per chain.",
        requiredTier = TierType.SILVER,
        balanceText = "2,340 VULT",
        thresholdText = "3,000 VULT",
        isBelowThreshold = true,
        onGetVult = {},
        onDismiss = {},
    )
}
