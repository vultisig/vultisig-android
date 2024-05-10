package com.vultisig.wallet.presenter.vault_setting.vault_detail

data class VaultDetailUiModel(
    val name:String = "",
    val pubKeyECDSA:String = "",
    val pubKeyEDDSA:String = "",
    val deviceList: List<String> = emptyList()
)