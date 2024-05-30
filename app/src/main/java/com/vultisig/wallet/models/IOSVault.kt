package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName

internal data class IOSVaultRoot(
    @SerializedName("vault")
    val vault: IOSVault,
    @SerializedName("version")
    val version: String,
)

internal data class IOSVault(
    @SerializedName("id")
    val id: String?,
    @SerializedName("coins")
    val coins: List<Coin>?,
    @SerializedName("localPartyID")
    val localPartyID: String,
    @SerializedName("pubKeyECDSA")
    val pubKeyECDSA: String,
    @SerializedName("hexChainCode")
    val hexChainCode: String,
    @SerializedName("pubKeyEdDSA")
    val pubKeyEdDSA: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("signers")
    val signers: List<String>,
    @SerializedName("createdAt")
    val createdAt: Float,
    @SerializedName("keyshares")
    val keyshares: List<IOSKeyShare>,
)

internal data class IOSKeyShare(
    @SerializedName("pubkey")
    val pubkey: String,
    @SerializedName("keyshare")
    val keyshare: String,
)