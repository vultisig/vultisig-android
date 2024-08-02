package com.vultisig.wallet.data.api.models

import com.google.gson.annotations.SerializedName

internal data class LiFiSwapQuoteJson(
    @SerializedName("estimate")
    val estimate: LiFiSwapEstimateJson,
    @SerializedName("transactionRequest")
    val transactionRequest: LiFiSwapTxJson,
)

internal data class LiFiSwapTxJson(
    @SerializedName("from")
    val from: String,
    @SerializedName("to")
    val to: String,
    @SerializedName("gasLimit")
    val gasLimit: String,
    @SerializedName("data")
    val data: String,
    @SerializedName("value")
    val value: String,
    @SerializedName("gasPrice")
    val gasPrice: String,
)

internal data class LiFiSwapEstimateJson(
    @SerializedName("toAmount")
    val toAmount: String,
)