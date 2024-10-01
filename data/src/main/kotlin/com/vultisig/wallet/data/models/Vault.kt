package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.utils.ServerUtils.LOCAL_PARTY_ID_PREFIX

data class Vault(
    val id: String,
    var name: String,
    var pubKeyECDSA: String = "",
    var pubKeyEDDSA: String = "",
    var hexChainCode: String = "",
    var localPartyID: String = "",
    var signers: List<String> = listOf(),
    var resharePrefix: String = "",
    var keyshares: List<KeyShare> = listOf(),
    val coins: List<Coin> = emptyList(),
)

fun Vault.getVaultPart(): Int {
    return signers.indexOf(localPartyID) + 1
}

fun Vault.getSignersExceptLocalParty(): List<String> {
    return signers.filter { it != localPartyID }
}

fun Vault.containsServerSigner(): Boolean {
    return signers.firstOrNull { it.contains(LOCAL_PARTY_ID_PREFIX, ignoreCase = true) } != null
}

fun Vault.isFastVault(): Boolean {
    return containsServerSigner() && signers.size == 2
}