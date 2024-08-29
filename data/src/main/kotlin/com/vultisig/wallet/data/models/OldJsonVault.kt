package com.vultisig.wallet.data.models

import com.google.gson.annotations.SerializedName

data class OldJsonVaultRoot(
    @SerializedName("vault")
    val vault: OldJsonVault,
    @SerializedName("version")
    val version: String,
)

data class OldJsonVault(
    @SerializedName("id")
    val id: String?,
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
    val keyShares: List<OldJsonVaultKeyShare>,
)

data class OldJsonVaultKeyShare(
    @SerializedName("pubkey")
    val pubKey: String,
    @SerializedName("keyshare")
    val keyShare: String,
)