package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FyndQuoteRequestJson(
    @SerialName("orders") val orders: List<FyndOrderJson>,
    @SerialName("options") val options: FyndQuoteOptionsJson,
)

@Serializable
data class FyndOrderJson(
    @SerialName("token_in") val tokenIn: String,
    @SerialName("token_out") val tokenOut: String,
    @SerialName("amount") val amount: String,
    @SerialName("side") val side: String = "sell",
    @SerialName("sender") val sender: String,
    @SerialName("receiver") val receiver: String,
)

@Serializable
data class FyndQuoteOptionsJson(
    @SerialName("timeout_ms") val timeoutMs: Int,
    @SerialName("min_responses") val minResponses: Int,
    @SerialName("encoding_options") val encodingOptions: FyndEncodingOptionsJson,
)

@Serializable
data class FyndEncodingOptionsJson(
    // Fraction as a decimal string ("0.005" = 0.5%), as the hosted API documents it.
    @SerialName("slippage") val slippage: String,
    @SerialName("transfer_type") val transferType: String = "transfer_from",
    @SerialName("client_fee_params") val clientFeeParams: FyndClientFeeRequestJson? = null,
)

/**
 * The client fee half the app owns. Fynd's own `ClientFeeParams` also needs `max_contribution`,
 * `deadline` and the receiver's EIP-712 `signature`; the Vultisig proxy fills those in, signs, and
 * patches the signature into the calldata. The app never holds the fee signer key.
 */
@Serializable
data class FyndClientFeeRequestJson(
    @SerialName("bps") val bps: Int,
    @SerialName("receiver") val receiver: String,
)

@Serializable
data class FyndQuoteResponseJson(@SerialName("orders") val orders: List<FyndOrderQuoteJson>)

@Serializable
data class FyndOrderQuoteJson(
    @SerialName("status") val status: String,
    @SerialName("amount_in") val amountIn: String = "",
    @SerialName("amount_out") val amountOut: String = "",
    @SerialName("gas_estimate") val gasEstimate: String = "",
    @SerialName("gas_price") val gasPrice: String? = null,
    @SerialName("price_impact_bps") val priceImpactBps: Int? = null,
    @SerialName("transaction") val transaction: FyndTransactionJson? = null,
    @SerialName("fee_breakdown") val feeBreakdown: FyndFeeBreakdownJson? = null,
    // Set by the Vultisig proxy when it signs the client fee; absent on a direct, unsigned quote.
    @SerialName("client_fee_receiver") val clientFeeReceiver: String? = null,
    @SerialName("client_fee_deadline") val clientFeeDeadline: Long? = null,
)

@Serializable
data class FyndTransactionJson(
    @SerialName("to") val to: String,
    @SerialName("value") val value: String,
    @SerialName("data") val data: String,
    @SerialName("client_fee_signature_offset") val clientFeeSignatureOffset: Int? = null,
)

@Serializable
data class FyndFeeBreakdownJson(
    @SerialName("router_fee") val routerFee: String,
    @SerialName("client_fee") val clientFee: String,
    @SerialName("max_slippage") val maxSlippage: String,
    @SerialName("min_amount_received") val minAmountReceived: String,
)

@Serializable
data class FyndErrorResponseJson(
    @SerialName("error") val error: String,
    @SerialName("code") val code: String,
)
