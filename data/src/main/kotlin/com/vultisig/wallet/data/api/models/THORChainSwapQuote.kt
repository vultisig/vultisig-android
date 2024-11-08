@file:UseSerializers(BigIntegerSerializer::class)

package com.vultisig.wallet.data.api.models

import com.vultisig.wallet.data.utils.BigIntegerSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigInteger

sealed class THORChainSwapQuoteDeserialized {
    data class Result(val data: THORChainSwapQuote) : THORChainSwapQuoteDeserialized()
    data class Error(val error: THORChainSwapQuoteError) : THORChainSwapQuoteDeserialized()
}

@Serializable
data class  THORChainSwapQuoteError(
    @SerialName("error")
    val message: String
)

@Serializable
data class THORChainSwapQuote(
    @SerialName("dust_threshold")
    val dustThreshold: String?,
    @SerialName("expected_amount_out")
    val expectedAmountOut: String,
    @SerialName("expiry")
    val expiry: BigInteger,
    @SerialName("fees")
    val fees: Fees,
    @SerialName("inbound_address")
    val inboundAddress: String?,
    @SerialName("inbound_confirmation_blocks")
    val inboundConfirmationBlocks: BigInteger?,
    @SerialName("inbound_confirmation_seconds")
    val inboundConfirmationSeconds: BigInteger?,
    @SerialName("max_streaming_quantity")
    val maxStreamingQuantity: Int,
    @SerialName("memo")
    val memo: String?,
    @SerialName("notes")
    val notes: String,
    @SerialName("outbound_delay_blocks")
    val outboundDelayBlocks: BigInteger,
    @SerialName("outbound_delay_seconds")
    val outboundDelaySeconds: BigInteger,
    @SerialName("recommended_min_amount_in")
    val recommendedMinAmountIn: String,
    @SerialName("streaming_swap_blocks")
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