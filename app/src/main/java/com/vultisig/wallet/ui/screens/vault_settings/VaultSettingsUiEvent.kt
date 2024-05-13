package com.vultisig.wallet.ui.screens.vault_settings

sealed class VaultSettingsUiEvent {
    data class BackupSuccess(val vaultName: String) : VaultSettingsUiEvent()
    data object BackupFailed : VaultSettingsUiEvent()
    data class BackupFile(val vaultName:String) : VaultSettingsUiEvent()
}