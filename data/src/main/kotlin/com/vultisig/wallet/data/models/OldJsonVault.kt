package com.vultisig.wallet.data.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class OldJsonVaultRoot(
    @SerialName("vault")
    val vault: OldJsonVault,
    @SerialName("version")
    val version: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OldJsonVault(
    @SerialName("id")
    val id: String?,
    @SerialName("localPartyID")
    val localPartyID: String,
    @SerialName("pubKeyECDSA")
    val pubKeyECDSA: String,
    @SerialName("hexChainCode")
    val hexChainCode: String,
    @JsonNames("pubKeyEdDSA", "pubKeyEDDSA")
    val pubKeyEdDSA: String,
    @SerialName("name")
    val name: String,
    @SerialName("signers")
    val signers: List<String>,
    @SerialName("keyshares")
    val keyShares: List<OldJsonVaultKeyShare>,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OldJsonVaultKeyShare(
    @JsonNames("pubkey", "pubKey")
    val pubKey: String,
    @SerialName("keyshare")
    val keyShare: String,
)