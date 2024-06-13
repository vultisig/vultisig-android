package com.vultisig.wallet.data.api.models

import com.google.gson.annotations.SerializedName

internal data class OneInchSwapQuoteJson(
    @SerializedName("dstAmount")
    val dstAmount: String,
    @SerializedName("tx")
    val tx: OneInchSwapTxJson,
)

internal data class OneInchSwapTxJson(
    @SerializedName("from")
    val from: String,
    @SerializedName("to")
    val to: String,
    @SerializedName("gas")
    val gas: Long,
    @SerializedName("data")
    val data: String,
    @SerializedName("value")
    val value: String,
    @SerializedName("gasPrice")
    val gasPrice: String,
)