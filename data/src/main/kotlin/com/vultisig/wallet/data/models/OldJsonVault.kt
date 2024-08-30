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
    @SerializedName("pubKeyEdDSA", alternate = ["pubKeyEDDSA"])
    val pubKeyEdDSA: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("signers")
    val signers: List<String>,
    @SerializedName("keyshares")
    val keyShares: List<OldJsonVaultKeyShare>,
)

data class OldJsonVaultKeyShare(
    @SerializedName("pubkey", alternate = ["pubKey"])
    val pubKey: String,
    @SerializedName("keyshare")
    val keyShare: String,
)