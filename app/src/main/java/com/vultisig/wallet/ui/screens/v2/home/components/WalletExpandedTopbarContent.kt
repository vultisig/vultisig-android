package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.VaultAccountsUiModel

@Composable
internal fun WalletExpandedTopbarContent(
    state: VaultAccountsUiModel,
    onToggleBalanceVisibility: () -> Unit,
    onSend: () -> Unit,
    onSwap: () -> Unit,
    onBuy: () -> Unit,
) {
    UiSpacer(
        40.dp
    )
    BalanceBanner(
        isVisible = state.isBalanceValueVisible,
        balance = state.totalFiatValue,
        onToggleVisibility = onToggleBalanceVisibility
    )

    UiSpacer(32.dp)

    TxButtons(
        isSwapEnabled = state.isSwapEnabled,
        onSend = onSend,
        onSwap = onSwap,
        onBuy = onBuy
    )
}