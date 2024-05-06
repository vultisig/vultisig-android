package com.vultisig.wallet.presenter.list_of_vault_and_details_list


import com.vultisig.wallet.models.Vault


data class VaultListAndDetailsState(
    val selectedItem:Vault? = null,
    val listOfVaultNames:MutableList<Vault> = mutableListOf<Vault>(),
    val listOfVaultCoins:MutableList<DetailsItem> = mutableListOf(),
    val isMainListVisible:Boolean = true,
    val loadingData:Boolean = false,
    val centerText: String = ""
)