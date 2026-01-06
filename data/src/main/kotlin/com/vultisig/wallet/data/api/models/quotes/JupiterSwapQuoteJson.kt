package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface JupiterSwapQuoteDeserialized {
    data class Result(val data: QuoteSwapTotalDataJson) : JupiterSwapQuoteDeserialized
    data class Error(val error: JupiterSwapQuoteError) : JupiterSwapQuoteDeserialized
}


@Serializable
data class JupiterSwapQuoteError(
    @SerialName("error")
    val error: String,
)

@Serializable
data class QuoteSwapTotalDataJson(
    @SerialName("quoteSwapData")
    val swapTransaction: QuoteSwapTransactionJson,
    @SerialName("dstAmount")
    val dstAmount: String,
    @SerialName("routePlan")
    val routePlan: List<RoutePlanItemJson>,
)

@Serializable
data class QuoteSwapTransactionJson(
    @SerialName("swapTransaction")
    val data: String,
)


@Serializable
data class SwapRouteResponseJson(
    @SerialName("inputMint")
    val inputMint: String,
    @SerialName("inAmount")
    val inAmount: String,
    @SerialName("outputMint")
    val outputMint: String,
    @SerialName("outAmount")
    val outAmount: String,
    @SerialName("otherAmountThreshold")
    val otherAmountThreshold: String,
    @SerialName("swapMode")
    val swapMode: String,
    @SerialName("slippageBps")
    val slippageBps: Int,
    @SerialName("priceImpactPct")
    val priceImpactPct: String,
    @SerialName("routePlan")
    val routePlan: List<RoutePlanItemJson>,
    @SerialName("contextSlot")
    val contextSlot: Long,
    @SerialName("timeTaken")
    val timeTaken: Double
)

@Serializable
data class RoutePlanItemJson(
    @SerialName("swapInfo")
    val swapInfo: SwapInfoJson,
    @SerialName("percent")
    val percent: Int
)

@Serializable
data class SwapInfoJson(
    @SerialName("ammKey")
    val ammKey: String,
    @SerialName("label")
    val label: String,
    @SerialName("inputMint")
    val inputMint: String,
    @SerialName("outputMint")
    val outputMint: String,
    @SerialName("inAmount")
    val inAmount: String,
    @SerialName("outAmount")
    val outAmount: String,
    @SerialName("feeAmount")
    val feeAmount: String? = null,
    @SerialName("feeMint")
    val feeMint: String? = null
)