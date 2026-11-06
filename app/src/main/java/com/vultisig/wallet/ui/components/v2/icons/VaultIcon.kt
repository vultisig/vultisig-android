package com.vultisig.wallet.ui.components.v2.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultIcon(
    modifier: Modifier = Modifier,
    isFastVault: Boolean,
    size: Dp = 16.dp,
    isSecureIconFilled: Boolean = false,
    contentDescription: String? =
        stringResource(if (isFastVault) R.string.name_vault_fast else R.string.name_vault_secure),
) {
    UiIcon(
        modifier = modifier,
        drawableResId =
            if (isFastVault) {
                R.drawable.thunder
            } else if (isSecureIconFilled) {
                R.drawable.icon_shield_solid
            } else {
                R.drawable.ic_shield
            },
        contentDescription = contentDescription,
        size = size,
        tint =
            if (isFastVault) {
                Theme.v2.colors.alerts.warning
            } else {
                Theme.v2.colors.alerts.success
            },
    )
}
