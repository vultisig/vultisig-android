package com.vultisig.wallet.ui.screens.vault_settings

sealed class VaultSettingsUiEvent {
    data class BackupSuccess(val backupFileName: String) :
        VaultSettingsUiEvent()

    data object BackupFailed : VaultSettingsUiEvent()
    data class BackupFile(val vaultName: String, val backupFileName: String) :
        VaultSettingsUiEvent()
}