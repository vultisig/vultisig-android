package com.vultisig.wallet.data.models

import com.google.gson.annotations.SerializedName

data class Vault(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    var name: String,
    @SerializedName("pubKeyECDSA")
    var pubKeyECDSA: String = "",
    @SerializedName("pubKeyEDDSA")
    var pubKeyEDDSA: String = "",
    @SerializedName("hexChainCode")
    var hexChainCode: String = "",
    @SerializedName("localPartyID")
    var localPartyID: String = "",
    @SerializedName("signers")
    var signers: List<String> = listOf(),
    @SerializedName("resharePrefix")
    var resharePrefix: String = "",
    @SerializedName("keyshares")
    var keyshares: List<KeyShare> = listOf(),
    @SerializedName("coins")
    val coins: List<Coin> = emptyList(),
)

fun Vault.getVaultPart(): Int {
    return signers.indexOf(localPartyID) + 1
}

fun Vault.containsServerSigner(): Boolean {
    return signers.firstOrNull { it.contains("server", ignoreCase = true) } != null
}

fun Vault.isFastVault(): Boolean {
    return containsServerSigner() && signers.size == 2
}