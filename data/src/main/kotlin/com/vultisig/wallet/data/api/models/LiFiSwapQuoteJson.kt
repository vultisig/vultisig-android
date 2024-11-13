package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface LiFiSwapQuoteResponse {
    data class Result(val data: LiFiSwapQuoteJson) : LiFiSwapQuoteResponse
    data class Error(val error: LiFiSwapQuoteError) : LiFiSwapQuoteResponse
}


@Serializable
data class LiFiSwapQuoteError(
    @SerialName("message")
    val message: String
)


@Serializable
data class LiFiSwapQuoteJson(
    @SerialName("estimate")
    val estimate: LiFiSwapEstimateJson,
    @SerialName("transactionRequest")
    val transactionRequest: LiFiSwapTxJson,
    @SerialName("message")
    val message: String? = null,
)

@Serializable
data class LiFiSwapTxJson(
    @SerialName("from")
    val from: String,
    @SerialName("to")
    val to: String,
    @SerialName("gasLimit")
    val gasLimit: String,
    @SerialName("data")
    val data: String,
    @SerialName("value")
    val value: String,
    @SerialName("gasPrice")
    val gasPrice: String,
)

@Serializable
data class LiFiSwapEstimateJson(
    @SerialName("toAmount")
    val toAmount: String,
)