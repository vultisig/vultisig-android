package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FourByteResponseJson (
    @SerialName("results")
    val list: List<FourByteResponseItem>
)

@Serializable
internal data class FourByteResponseItem (
    @SerialName("id")
    val id: Int,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("text_signature")
    val textSignature: String,
    @SerialName("hex_signature")
    val hexSignature: String,
)