package com.vultisig.wallet.ui.screens.vault_settings

import com.vultisig.wallet.common.UiText

sealed class VaultSettingsUiEvent {
    data class NavigateToScreen(val route:String):VaultSettingsUiEvent()
    data class ShowSnackBar(val message: UiText) : VaultSettingsUiEvent()
    data class BackupFile(val vaultName:String) : VaultSettingsUiEvent()
}