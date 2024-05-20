package com.vultisig.wallet.ui.screens.vault_settings


sealed class VaultSettingsEvent {
    data object ShowConfirmDeleteScreen : VaultSettingsEvent()
    data object DismissConfirmDeleteScreen : VaultSettingsEvent()
    data class ChangeCheckCaution(val index: Int, val isChecked: Boolean) : VaultSettingsEvent()
    data object Delete : VaultSettingsEvent()
    data object Backup : VaultSettingsEvent()
    data object ErrorDownloadFile : VaultSettingsEvent()
    data class SuccessBackup(val fileName: String) : VaultSettingsEvent()
}