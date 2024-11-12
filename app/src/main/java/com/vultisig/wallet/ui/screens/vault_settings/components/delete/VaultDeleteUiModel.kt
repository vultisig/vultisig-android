package com.vultisig.wallet.ui.screens.vault_settings.components.delete

data class VaultDeleteUiModel (
    val name: String = "",
    val totalFiatValue: String? = null,
    val pubKeyECDSA: String = "",
    val pubKeyEDDSA: String = "",
    val deviceList: List<String> = emptyList(),
    val localPartyId: String = "",
    val vaultPart: Int = 0,
)