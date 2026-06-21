package com.vultisig.wallet.ui.screens.swap.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.models.swap.VultTierGateUiModel
import com.vultisig.wallet.ui.theme.Theme

// Silver-tier footer gradient (top → bottom). Tier palette isn't in the theme tokens, so the two
// stops are defined here as private constants per the design (#4858).
private val SilverGradientTop = Color(0xFF7D8B9E)
private val SilverGradientBottom = Color(0xFFC9D6E8)

/**
 * Tier-locked upsell shown when a vault below the Silver $VULT tier taps Advanced Settings (#4858).
 * Presents the feature, the Silver requirement, the vault's current $VULT balance, and a "Get
 * $VULT" action that routes to a swap pre-filled with VULT — mirroring the iOS LockedFeatureSheet.
 */
@Composable
internal fun SwapAdvancedSettingsLockedSheet(
    gate: VultTierGateUiModel,
    onGetVult: () -> Unit,
    onDismiss: () -> Unit,
) {
    V2BottomSheet(
        onDismissRequest = onDismiss,
        leftAction = {
            VsCircleButton(
                onClick = onDismiss,
                drawableResId = R.drawable.big_close,
                size = VsCircleButtonSize.Small,
                type = VsCircleButtonType.Tertiary,
            )
        },
    ) {
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
                    drawableResId = R.drawable.ic_settings_slider_ver,
                    size = 24.dp,
                    tint = Theme.v2.colors.primary.accent4,
                )
            }

            UiSpacer(24.dp)

            Text(
                text = stringResource(R.string.swap_advanced_locked_title),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(12.dp)

            Text(
                text = stringResource(R.string.swap_advanced_locked_subtitle),
                style = Theme.brockmann.body.s.regular,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            UiSpacer(24.dp)

            RequiresCard(gate = gate, onGetVult = onGetVult)
        }
    }
}

@Composable
private fun RequiresCard(gate: VultTierGateUiModel, onGetVult: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, Theme.v2.colors.border.light, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(Theme.v2.colors.backgrounds.secondary)
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.swap_advanced_locked_requires),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.type_silver_tier__size_small),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text =
                            stringResource(
                                R.string.swap_advanced_locked_tier_requirement,
                                stringResource(R.string.vault_tier_silver),
                            ),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.swap_advanced_locked_tier_subtitle,
                                gate.thresholdText,
                            ),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.tertiary,
                    )
                }
            }

            BalanceRow(gate = gate)
        }

        // Gradient "Get $VULT" footer joined to the card's bottom edge (single rounded container).
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.verticalGradient(listOf(SilverGradientTop, SilverGradientBottom))
                    )
                    .clickable(onClick = onGetVult),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.swap_advanced_locked_get_vult),
                style = Theme.brockmann.button.semibold.semibold,
                color = Theme.v2.colors.text.primary,
            )
        }
    }
}

@Composable
private fun BalanceRow(gate: VultTierGateUiModel) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Theme.v2.colors.backgrounds.tertiary)
                .border(1.dp, Theme.v2.colors.border.light, RoundedCornerShape(14.dp))
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
            text = stringResource(R.string.swap_advanced_locked_your_balance),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = gate.balanceText,
            style = Theme.brockmann.supplementary.caption,
            color =
                if (gate.isBelowThreshold) Theme.v2.colors.alerts.warning
                else Theme.v2.colors.text.primary,
        )
    }
}
