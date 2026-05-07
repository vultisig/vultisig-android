package com.vultisig.wallet.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

internal data class ChainDashboardTopBarAction(@DrawableRes val icon: Int, val onClick: () -> Unit)

internal val LocalChainDashboardTopBarActionSetter =
    staticCompositionLocalOf<(ChainDashboardTopBarAction?) -> Unit> { {} }

@Composable
internal fun RegisterChainDashboardTopBarAction(
    @DrawableRes icon: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val setter = LocalChainDashboardTopBarActionSetter.current
    val onClickState by rememberUpdatedState(onClick)
    LaunchedEffect(setter, icon, enabled) {
        setter(if (enabled) ChainDashboardTopBarAction(icon = icon, onClick = onClickState) else null)
    }
}
