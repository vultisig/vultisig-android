package com.vultisig.wallet.ui.screens.vault_settings.components

data class VaultDeleteUiModel (
    var name:String = "",
    val pubKeyECDSA:String = "",
    val pubKeyEDDSA:String = "",
    val deviceList: List<String> = emptyList()
)