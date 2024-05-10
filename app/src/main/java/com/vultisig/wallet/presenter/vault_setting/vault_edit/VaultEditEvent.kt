package com.vultisig.wallet.presenter.vault_setting.vault_edit

sealed class VaultEditEvent {
    data class OnNameChange(val name: String) : VaultEditEvent()
    data object OnSave : VaultEditEvent()
}