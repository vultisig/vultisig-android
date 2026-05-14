package com.vultisig.wallet.data.api.models.signer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for the VultiSigner `POST /vault/batch/reshare` endpoint.
 *
 * Used when the initiator opts the ceremony into parallel ECDSA + EdDSA reshare. The server runs
 * both protocols concurrently and routes their TSS traffic through the per-protocol relay channels
 * named in [protocols].
 *
 * The wire shape matches iOS `BatchReshareRequest` and Windows `batchReshareWithServer` —
 * deliberately narrower than [JoinReshareRequestJson]: the server already knows `name`,
 * `hex_chain_code`, `lib_type`, and the previous `old_reshare_prefix` from the existing vault keyed
 * by [publicKeyEcdsa].
 */
@Serializable
data class BatchReshareRequestJson(
    @SerialName("public_key") val publicKeyEcdsa: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("hex_encryption_key") val hexEncryptionKey: String,
    @SerialName("local_party_id") val localPartyId: String,
    @SerialName("old_parties") val oldParties: List<String>,
    @SerialName("encryption_password") val encryptionPassword: String,
    @SerialName("email") val email: String,
    @SerialName("protocols") val protocols: List<String>,
) {
    companion object {
        const val PROTOCOL_ECDSA = "ecdsa"
        const val PROTOCOL_EDDSA = "eddsa"
    }
}
