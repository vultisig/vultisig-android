package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiSpacer

/** The home header's balance and actions, shared by the Wallet and DeFi tabs. */
@Composable
internal fun HomeExpandedTopbarContent(
    balance: String?,
    isBalanceVisible: Boolean,
    isSwapEnabled: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    onSend: () -> Unit,
    onSwap: () -> Unit,
    onBuy: () -> Unit,
    onReceive: () -> Unit,
) {
    UiSpacer(40.dp)
    BalanceBanner(
        isVisible = isBalanceVisible,
        balance = balance,
        onToggleVisibility = onToggleBalanceVisibility,
    )

    UiSpacer(32.dp)

    TxButtons(
        isSwapEnabled = isSwapEnabled,
        onSend = onSend,
        onSwap = onSwap,
        onBuy = onBuy,
        onReceive = onReceive,
    )
}
