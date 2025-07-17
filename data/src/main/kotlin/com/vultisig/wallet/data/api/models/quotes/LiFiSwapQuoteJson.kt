package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface LiFiSwapQuoteDeserialized {
    data class Result(val data: LiFiSwapQuoteJson) : LiFiSwapQuoteDeserialized
    data class Error(val error: LiFiSwapQuoteError) : LiFiSwapQuoteDeserialized
}


@Serializable
data class LiFiSwapQuoteError(
    @SerialName("message")
    val message: String,
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
    val from: String?,
    @SerialName("to")
    val to: String?,
    @SerialName("gasLimit")
    val gasLimit: String?,
    @SerialName("data")
    val data: String,
    @SerialName("value")
    val value: String?,
    @SerialName("gasPrice")
    val gasPrice: String?,
)

@Serializable
data class LiFiSwapEstimateJson(
    @SerialName("toAmount")
    val toAmount: String,
    @SerialName("feeCosts")
    val feeCosts: List<LiFiSwapFeeCostJson>,
)

@Serializable
data class LiFiSwapFeeCostJson(
    @SerialName("amount")
    val amount: String,
    @SerialName("included")
    val included: Boolean,
    @SerialName("name")
    val name: String,
    @SerialName("token")
    val token: LiFiSwapTokenJson?,
)

@Serializable
data class LiFiSwapTokenJson(
    @SerialName("address")
    val address: String,
)