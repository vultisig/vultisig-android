package com.vultisig.wallet.ui.screens.swap.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.swap.VultTierGateUiModel
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.screens.settings.bottomsheets.FeatureGateBottomSheet

/**
 * Tier-locked upsell shown when a vault below the Silver $VULT tier taps Advanced Settings (#4858).
 * Thin wrapper that feeds the Advanced Swap feature copy into the shared [FeatureGateBottomSheet].
 */
@Composable
internal fun SwapAdvancedSettingsLockedSheet(
    gate: VultTierGateUiModel,
    onGetVult: () -> Unit,
    onDismiss: () -> Unit,
) {
    FeatureGateBottomSheet(
        featureIcon = R.drawable.ic_settings_slider_ver,
        featureTitle = stringResource(R.string.swap_advanced_locked_title),
        featureDescription = stringResource(R.string.swap_advanced_locked_subtitle),
        requiredTier = TierType.SILVER,
        balanceText = gate.balanceText,
        thresholdText = gate.thresholdText,
        isBelowThreshold = gate.isBelowThreshold,
        onGetVult = onGetVult,
        onDismiss = onDismiss,
    )
}
