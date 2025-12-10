package com.vultisig.wallet.data.api.models.signer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JoinKeysignRequestJson(
    @SerialName("public_key")
    val publicKeyEcdsa: String,
    @SerialName("messages")
    val messages: List<String>,
    @SerialName("session")
    val sessionId: String,
    @SerialName("hex_encryption_key")
    val hexEncryptionKey: String,
    @SerialName("derive_path")
    val derivePath: String,
    @SerialName("is_ecdsa")
    val isEcdsa: Boolean,
    @SerialName("vault_password")
    val password: String,
    @SerialName("chain")
    val chain: String,
)