package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType

@Composable
internal fun TopRow(
    vaultName: String,
    isFastVault : Boolean,
    onOpenHistoryClick: () -> Unit = {},
    onOpenSettingsClick: () -> Unit = {},
    onToggleVaultListClick: () -> Unit = {},
) {
    Row {
        ChooseVaultButton(
            vaultName = vaultName,
            isFastVault = isFastVault,
            onClick = onToggleVaultListClick
        )
        UiSpacer(
            weight = 1f
        )

        VsCircleButton(
            onClick = onOpenHistoryClick,
            icon = R.drawable.ic_arrow_bottom_top,
            size = VsCircleButtonSize.Small,
            type = VsCircleButtonType.Secondary,
            designType = DesignType.Shined,
        )
        UiSpacer(size = 8.dp)
        VsCircleButton(
            onClick = onOpenSettingsClick,
            icon = R.drawable.gear,
            size = VsCircleButtonSize.Small,
            type = VsCircleButtonType.Secondary,
            designType = DesignType.Shined,
        )
    }
}