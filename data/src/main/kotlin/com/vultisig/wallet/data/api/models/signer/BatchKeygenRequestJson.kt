package com.vultisig.wallet.data.api.models.signer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for the VultiSigner `POST /vault/batch/keygen` endpoint.
 *
 * This is used when parallel keygen is enabled. The server runs all [protocols] in parallel
 * goroutines. Same fields as [JoinKeygenRequestJson] but replaces `lib_type` with a `protocols`
 * list.
 */
@Serializable
data class BatchKeygenRequestJson(
    @SerialName("name") val vaultName: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("hex_encryption_key") val hexEncryptionKey: String,
    @SerialName("hex_chain_code") val hexChainCode: String,
    @SerialName("local_party_id") val localPartyId: String,
    @SerialName("encryption_password") val encryptionPassword: String,
    @SerialName("email") val email: String,
    @SerialName("lib_type") val libType: Int,
    @SerialName("protocols") val protocols: List<String>,
) {
    companion object {
        const val PROTOCOL_ECDSA = "ecdsa"
        const val PROTOCOL_EDDSA = "eddsa"
    }
}
