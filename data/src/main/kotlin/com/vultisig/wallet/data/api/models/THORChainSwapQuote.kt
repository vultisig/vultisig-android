package com.vultisig.wallet.data.api.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.math.BigInteger

sealed class THORChainSwapQuoteDeserialized {
    data class Result(val data: THORChainSwapQuote) : THORChainSwapQuoteDeserialized()
    data class Error(val error: THORChainSwapQuoteError) : THORChainSwapQuoteDeserialized()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class THORChainSwapQuoteError(
    @JsonNames("error", "message")
    val message: String,
)

@Serializable
data class THORChainSwapQuote(
    @SerialName("dust_threshold")
    val dustThreshold: String?,
    @SerialName("expected_amount_out")
    val expectedAmountOut: String,
    @SerialName("expiry")
    @Contextual
    val expiry: BigInteger,
    @SerialName("fees")
    val fees: Fees,
    @SerialName("inbound_address")
    val inboundAddress: String?,
    @SerialName("inbound_confirmation_blocks")
    @Contextual
    val inboundConfirmationBlocks: BigInteger?,
    @Contextual
    @SerialName("inbound_confirmation_seconds")
    val inboundConfirmationSeconds: BigInteger?,
    @SerialName("max_streaming_quantity")
    val maxStreamingQuantity: Int,
    @SerialName("memo")
    val memo: String?,
    @SerialName("notes")
    val notes: String,
    @SerialName("outbound_delay_blocks")
    @Contextual
    val outboundDelayBlocks: BigInteger,
    @SerialName("outbound_delay_seconds")
    @Contextual
    val outboundDelaySeconds: BigInteger,
    @SerialName("recommended_min_amount_in")
    val recommendedMinAmountIn: String,
    @SerialName("streaming_swap_blocks")
    @Contextual
    val streamingSwapBlocks: BigInteger,
    @SerialName("total_swap_seconds")
    val totalSwapSeconds: Long?,
    @SerialName("warning")
    val warning: String,
    @SerialName("router")
    val router: String?,
    @SerialName("error")
    val error: String?,
)

@Serializable
data class Fees(
    @SerialName("affiliate")
    val affiliate: String,
    @SerialName("asset")
    val asset: String,
    @SerialName("outbound")
    val outbound: String,
    @SerialName("total")
    val total: String,
)