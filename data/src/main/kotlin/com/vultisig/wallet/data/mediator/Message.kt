package com.vultisig.wallet.data.mediator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    @SerialName("session_id") val sessionID: String,
    @SerialName("from") val from: String,
    @SerialName("to") val to: List<String>,
    @SerialName("body") val body: String,
    @SerialName("hash") val hash: String,
    @SerialName("sequence_no") val sequenceNo: Int,
)