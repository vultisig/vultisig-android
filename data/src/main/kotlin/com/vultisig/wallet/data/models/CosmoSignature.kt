package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.utils.Numeric
import io.ktor.util.decodeBase64Bytes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class CosmoSignature(
    @SerialName("mode")
    val mode: String,
    @SerialName("tx_bytes")
    val txBytes: String
)

fun CosmoSignature.transactionHash(): String {
    val decodedBytes = txBytes.decodeBase64Bytes()
    val digest = MessageDigest.getInstance("SHA-256").digest(decodedBytes)
    return Numeric.toHexStringNoPrefix(digest)
}