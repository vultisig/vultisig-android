package com.vultisig.wallet.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.ui.models.VaultAccountsViewModel
import com.vultisig.wallet.ui.screens.scan.ScanQrBottomSheet
import com.vultisig.wallet.ui.screens.v2.home.HomePage

@Composable
internal fun VaultAccountsScreen(
    viewModel: VaultAccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    if (state.showMonthlyBackupReminder) {
        MonthlyBackupReminder(
            onDismiss = viewModel::dismissBackupReminder,
            onBackup = viewModel::backupVault,
            onDoNotRemind = viewModel::doNotRemindBackup,
        )
    }
    if (state.showCameraBottomSheet) {
        ScanQrBottomSheet (
            onDismiss = viewModel::dismissCameraBottomSheet,
            onScanSuccess = viewModel::onScanSuccess,
        )
    }

    HomePage(
        state = state,
        onRefresh = viewModel::refreshData,
        onSend = viewModel::send,
        onSwap = viewModel::swap,
        onBuy = viewModel::buy,
        openCamera = viewModel::openCamera,
        onAccountClick = viewModel::openAccount,
        onToggleBalanceVisibility = viewModel::toggleBalanceVisibility,
        onOpenSettingsClick = viewModel::openSettings,
        onToggleVaultListClick = viewModel::openVaultList,
        onChooseChains = viewModel::openAddChainAccount,
        onMigrateClick = viewModel::migrate,
        onDismissBanner = viewModel::tempRemoveBanner,
        onCryptoConnectionTypeClick = viewModel::setCryptoConnectionType,
    )

}

internal object VaultAccountsScreenTags {
    const val MIGRATE = "VaultAccountsScreen.migrate"
}