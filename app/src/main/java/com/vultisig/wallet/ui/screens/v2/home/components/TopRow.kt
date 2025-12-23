package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.DesignType
import com.vultisig.wallet.ui.components.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.buttons.VsCircleButtonType

@Composable
internal fun TopRow(
    vaultName: String,
    isFastVault : Boolean,
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
            onClick = onOpenSettingsClick,
            icon = R.drawable.gear,
            size = VsCircleButtonSize.Small,
            type = VsCircleButtonType.Secondary,
            designType = DesignType.Shined,
        )
    }
}