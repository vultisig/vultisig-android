package com.vultisig.wallet.data.api.models.signer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JoinReshareRequestJson(
    @SerialName("name")
    val vaultName: String,
    @SerialName("public_key")
    val publicKeyEcdsa: String?,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("hex_encryption_key")
    val hexEncryptionKey: String,
    @SerialName("hex_chain_code")
    val hexChainCode: String,
    @SerialName("local_party_id")
    val localPartyId: String,
    @SerialName("encryption_password")
    val encryptionPassword: String,
    @SerialName("email")
    val email: String,
    @SerialName("old_parties")
    val oldParties: List<String>,
    @SerialName("old_reshare_prefix")
    val oldResharePrefix: String,
)