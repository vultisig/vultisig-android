package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName

internal data class Vault(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    var name: String,
    @SerializedName("backedUp")
    var backedUp: Boolean = false,
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
