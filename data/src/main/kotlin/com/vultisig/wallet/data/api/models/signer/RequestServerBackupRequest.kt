package com.vultisig.wallet.data.api.models.signer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request body for the VultiSigner `POST /vault/resend` endpoint. */
@Serializable
internal data class RequestServerBackupRequest(
    @SerialName("public_key_ecdsa") val publicKeyEcdsa: String,
    val password: String,
    val email: String,
)
