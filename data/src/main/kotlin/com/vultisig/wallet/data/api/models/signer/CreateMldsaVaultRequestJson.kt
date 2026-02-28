package com.vultisig.wallet.data.api.models.signer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateMldsaVaultRequestJson(
    @SerialName("public_key")
    val publicKey: String,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("hex_encryption_key")
    val hexEncryptionKey: String,
    @SerialName("encryption_password")
    val encryptionPassword: String,
    @SerialName("email")
    val email: String,
)
