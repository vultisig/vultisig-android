package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body sent to SwapKit `POST /v3/quote`. The Vultisig proxy attaches the partner API key
 * server-side; no `x-api-key` is set on the device.
 */
@Serializable
data class SwapKitQuoteRequest(
    @SerialName("sellAsset") val sellAsset: String,
    @SerialName("buyAsset") val buyAsset: String,
    @SerialName("sellAmount") val sellAmount: String,
    @SerialName("sourceAddress") val sourceAddress: String,
    @SerialName("destinationAddress") val destinationAddress: String,
    @SerialName("slippage") val slippage: Double = DEFAULT_SLIPPAGE,
    @SerialName("affiliate") val affiliate: String = DEFAULT_AFFILIATE,
    @SerialName("affiliateFee") val affiliateBps: Int = 0,
    @SerialName("includeTx") val includeTx: Boolean = false,
) {
    companion object {
        /** Until the partner-dashboard affiliate ids are set up Vultisig collects nothing. */
        const val DEFAULT_AFFILIATE: String = "notconfigured.near"
        const val DEFAULT_SLIPPAGE: Double = 1.0
    }
}

/**
 * Response envelope returned by `POST /v3/quote`. Each [SwapKitRoute] is a candidate path; the
 * client filters out THORChain/Maya, drops multi-hop, then ranks the survivors by
 * [SwapKitRoute.expectedBuyAmount].
 */
@Serializable
data class SwapKitQuoteResponseJson(
    @SerialName("routes") val routes: List<SwapKitRoute> = emptyList(),
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null,
)

/**
 * A single candidate quote in the SwapKit `/v3/quote` response. [providers] lists the sub-providers
 * that compose the route (e.g. `["CHAINFLIP"]`); routes with more than one entry are filtered out
 * client-side until multi-hop is enabled in a later phase.
 */
@Serializable
data class SwapKitRoute(
    @SerialName("providers") val providers: List<String> = emptyList(),
    @SerialName("sellAsset") val sellAsset: String = "",
    @SerialName("buyAsset") val buyAsset: String = "",
    @SerialName("sellAmount") val sellAmount: String = "",
    @SerialName("expectedBuyAmount") val expectedBuyAmount: String = "0",
    @SerialName("expectedBuyAmountMaxSlippage") val expectedBuyAmountMaxSlippage: String? = null,
    @SerialName("sourceAddress") val sourceAddress: String? = null,
    @SerialName("destinationAddress") val destinationAddress: String? = null,
    @SerialName("targetAddress") val targetAddress: String? = null,
    @SerialName("estimatedTime") val estimatedTime: SwapKitEstimatedTime? = null,
    @SerialName("totalSlippageBps") val totalSlippageBps: Double? = null,
    @SerialName("legs") val legs: List<SwapKitRouteLeg> = emptyList(),
    @SerialName("fees") val fees: List<SwapKitFee> = emptyList(),
    @SerialName("warnings") val warnings: List<SwapKitWarning> = emptyList(),
    @SerialName("meta") val meta: SwapKitRouteMeta? = null,
) {
    /** Lower-cased provider id used by the THORChain/Maya filter and the sub-provider tag. */
    val primaryProviderId: String
        get() = providers.firstOrNull().orEmpty().lowercase()
}

@Serializable
data class SwapKitRouteLeg(
    @SerialName("provider") val provider: String = "",
    @SerialName("sellAsset") val sellAsset: String = "",
    @SerialName("buyAsset") val buyAsset: String = "",
    @SerialName("sellAmount") val sellAmount: String? = null,
    @SerialName("buyAmount") val buyAmount: String? = null,
)

@Serializable
data class SwapKitEstimatedTime(
    @SerialName("inbound") val inbound: Double? = null,
    @SerialName("swap") val swap: Double? = null,
    @SerialName("outbound") val outbound: Double? = null,
    @SerialName("total") val total: Double? = null,
)

@Serializable
data class SwapKitFee(
    @SerialName("type") val type: String? = null,
    @SerialName("amount") val amount: String? = null,
    @SerialName("asset") val asset: String? = null,
    @SerialName("chain") val chain: String? = null,
    @SerialName("protocol") val protocol: String? = null,
)

@Serializable
data class SwapKitWarning(
    @SerialName("code") val code: String? = null,
    @SerialName("display") val display: String? = null,
)

@Serializable
data class SwapKitRouteMeta(
    @SerialName("priceImpact") val priceImpact: Double? = null,
    @SerialName("quoteMode") val quoteMode: String? = null,
)
