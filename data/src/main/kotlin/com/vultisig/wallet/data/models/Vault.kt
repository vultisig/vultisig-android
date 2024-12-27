package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.utils.ServerUtils.LOCAL_PARTY_ID_PREFIX

typealias VaultId = String

data class Vault(
    val id: VaultId,
    var name: String,
    var pubKeyECDSA: String = "",
    var pubKeyEDDSA: String = "",
    var hexChainCode: String = "",
    var localPartyID: String = "",
    var signers: List<String> = listOf(),
    var resharePrefix: String = "",
    var keyshares: List<KeyShare> = listOf(),
    val coins: List<Coin> = emptyList(),
    var libType: SigningLibType = SigningLibType.GG20,
)

enum class SigningLibType { DKLS, GG20 }

fun Vault.getVaultPart(): Int {
    return signers.indexOf(localPartyID) + 1
}

fun Vault.getSignersExceptLocalParty(): List<String> {
    return signers.filter { it != localPartyID }
}

fun Vault.containsServerSigner(): Boolean {
    return signers.firstOrNull { it.contains(LOCAL_PARTY_ID_PREFIX, ignoreCase = true) } != null
}

fun Vault.isServerVault(): Boolean {
    return localPartyID.contains(LOCAL_PARTY_ID_PREFIX, ignoreCase = true)
}

fun Vault.isFastVault(): Boolean {
    return containsServerSigner() && !isServerVault()
}