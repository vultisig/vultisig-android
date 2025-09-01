package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class EVMSwapQuoteDeserialized {
    data class Result(val data: EVMSwapQuoteJson) : EVMSwapQuoteDeserialized()
    data class Error(val error: String) : EVMSwapQuoteDeserialized()
}


@Serializable
data class EVMSwapQuoteJson(
    @SerialName("dstAmount")
    val dstAmount: String,
    @SerialName("tx")
    val tx: OneInchSwapTxJson,
    @SerialName("error")
    val error: String? = null,
)

@Serializable
data class OneInchQuoteJson(
    @SerialName("dstAmount")
    val dstAmount: String,
    @SerialName("gas")
    val gas: Long,
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
    @SerialName("swapFee")
    val swapFee: String = "",
    @SerialName("swapFeeTokenContract")
    val swapFeeTokenContract: String = "",
)

@Serializable
data class OneInchSwapQuoteErrorResponse(
    @SerialName("statusCode")
    val statusCode: Int,
    @SerialName("description")
    val description: String,
    @SerialName("error")
    val error: String
) {
    override fun toString(): String {
        return "OneInchSwapQuoteErrorResponse(code=$statusCode, message='$description')"
    }
}