package com.vultisig.wallet.data.api.models.signer

import com.vultisig.wallet.data.models.SigningLibType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JoinKeygenRequestJson(
    @SerialName("name")
    val vaultName: String,
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
    @SerialName("lib_type")
    val libType: Int, // 0 for GG20 , 1 for DKLS
)

fun SigningLibType.toJson(): Int {
    return when (this) {
        SigningLibType.GG20 -> 0
        SigningLibType.DKLS -> 1
        SigningLibType.KeyImport -> 2
    }
}