package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.common.Numeric
import okio.ByteString.Companion.decodeBase64
import java.security.MessageDigest

data class CosmoSignature(
    @SerializedName("mode")
    val mode: String,
    @SerializedName("tx_bytes")
    val tx_bytes: String
)

fun CosmoSignature.transactionHash(): String {
    val decodedBytes = tx_bytes.decodeBase64()?.toByteArray() ?: run { return "" }
    val digest = MessageDigest.getInstance("SHA-256").digest(decodedBytes)
    return Numeric.toHexStringNoPrefix(digest)
}