package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FourByteResponse (
    @SerialName("results")
    val list: List<FourByteResponceItem>
)

@Serializable
data class FourByteResponceItem (
    @SerialName("id")
    val id: Int,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("text_signature")
    val textSignature: String,
    @SerialName("hex_signature")
    val hexSignature: String,
)