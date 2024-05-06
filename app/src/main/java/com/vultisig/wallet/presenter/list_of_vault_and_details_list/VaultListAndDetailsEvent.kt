package com.vultisig.wallet.presenter.list_of_vault_and_details_list

import com.vultisig.wallet.models.Vault

sealed class VaultListAndDetailsEvent {
    data class OnItemClick(val value: Vault) : VaultListAndDetailsEvent()
    data class UpdateMainScreen(val isVisible: Boolean) : VaultListAndDetailsEvent()
}