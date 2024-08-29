package com.vultisig.wallet.data.models

import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.data.utils.Numeric
import io.ktor.util.decodeBase64Bytes
import java.security.MessageDigest

data class CosmoSignature(
    @SerializedName("mode")
    val mode: String,
    @SerializedName("tx_bytes")
    val txBytes: String
)

fun CosmoSignature.transactionHash(): String {
    val decodedBytes = txBytes.decodeBase64Bytes()
    val digest = MessageDigest.getInstance("SHA-256").digest(decodedBytes)
    return Numeric.toHexStringNoPrefix(digest)
}