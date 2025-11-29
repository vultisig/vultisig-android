package com.vultisig.wallet.ui.components.v2.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultIcon(
    isFastVault: Boolean,
    size: Dp = 16.dp,
) {
    UiIcon(
        drawableResId = if (isFastVault) {
            R.drawable.thunder
        } else {
            R.drawable.ic_shield
        },
        contentDescription = "vault type logo",
        size = size,
        tint = if (isFastVault) {
            Theme.v2.colors.alerts.warning
        } else {
            Theme.v2.colors.alerts.success
        },
    )
}