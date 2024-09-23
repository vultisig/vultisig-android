package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OneInchSwapQuoteJson(
    @SerialName("dstAmount")
    val dstAmount: String,
    @SerialName("tx")
    val tx: OneInchSwapTxJson,
    @SerialName("error")
    val error: String? = null,
)

@Serializable
data class OneInchSwapTxJson(
    @SerialName("from")
    val from: String,
    @SerialName("to")
    val to: String,
    @SerialName("gas")
    val gas: Long,
    @SerialName("data")
    val data: String,
    @SerialName("value")
    val value: String,
    @SerialName("gasPrice")
    val gasPrice: String,
)