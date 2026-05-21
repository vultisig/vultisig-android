package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body sent to SwapKit `POST /v3/quote` per the V3 spec at
 * https://docs.swapkit.dev/llms-full.txt. The Vultisig proxy attaches the partner `x-api-key`
 * server-side; affiliate identifiers (THORName, MAYAName, Chainflip broker, NEAR name) are
 * configured server-side via the partner dashboard, so no affiliate id is sent on the wire — only
 * the optional [affiliateFee] basis-points override is.
 */
@Serializable
data class SwapKitQuoteRequest(
    @SerialName("sellAsset") val sellAsset: String,
    @SerialName("buyAsset") val buyAsset: String,
    @SerialName("sellAmount") val sellAmount: String,
    /** Optional — V3 allows quoting before the wallet has picked an account. */
    @SerialName("sourceAddress") val sourceAddress: String? = null,
    /** Optional — V3 allows quoting before the wallet has picked an account. */
    @SerialName("destinationAddress") val destinationAddress: String? = null,
    /** Limits the liquidity providers considered. Omit to let SwapKit pick from all available. */
    @SerialName("providers") val providers: List<String>? = null,
    /** Max slippage as percentage. `1` means 1% (NOT basis points). */
    @SerialName("slippage") val slippage: Double = DEFAULT_SLIPPAGE,
    /** Affiliate fee override in basis points (range 0..1000, max 10%). */
    @SerialName("affiliateFee") val affiliateFee: Int = 0,
) {
    companion object {
        const val DEFAULT_SLIPPAGE: Double = 1.0
    }
}

/**
 * Response envelope returned by `POST /v3/quote`. Each [SwapKitRoute] is a candidate path; the
 * client filters out THORChain/Maya, drops multi-hop, then ranks the survivors by
 * [SwapKitRoute.expectedBuyAmount]. Phase 2+ may surface [providerErrors] in the UI.
 */
@Serializable
data class SwapKitQuoteResponseJson(
    @SerialName("quoteId") val quoteId: String? = null,
    @SerialName("routes") val routes: List<SwapKitRoute> = emptyList(),
    @SerialName("providerErrors") val providerErrors: List<SwapKitProviderError> = emptyList(),
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null,
)

/** Per-provider error surfaced alongside successful routes when other providers failed. */
@Serializable
data class SwapKitProviderError(
    @SerialName("provider") val provider: String? = null,
    @SerialName("errorCode") val errorCode: String? = null,
    @SerialName("message") val message: String? = null,
)

/**
 * A single candidate quote in the SwapKit `/v3/quote` response. [routeId] is the UUID the caller
 * must echo to `POST /v3/swap` to execute the route. [providers] lists the sub-providers that
 * compose the route (e.g. `["CHAINFLIP"]`); routes with more than one entry are filtered out
 * client-side until multi-hop is enabled in a later phase.
 */
@Serializable
data class SwapKitRoute(
    @SerialName("routeId") val routeId: String? = null,
    @SerialName("providers") val providers: List<String> = emptyList(),
    @SerialName("sellAsset") val sellAsset: String = "",
    @SerialName("buyAsset") val buyAsset: String = "",
    @SerialName("sellAmount") val sellAmount: String = "",
    /**
     * Nullable so a route with a missing or unparseable amount is distinguishable from a genuine
     * zero quote — the ranking path must handle absent amounts explicitly rather than treating them
     * as `BigDecimal.ZERO`.
     */
    @SerialName("expectedBuyAmount") val expectedBuyAmount: String? = null,
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

/** Single hop inside a [SwapKitRoute]. Phase 1 only honours routes with exactly one leg. */
@Serializable
data class SwapKitRouteLeg(
    @SerialName("provider") val provider: String = "",
    @SerialName("sellAsset") val sellAsset: String = "",
    @SerialName("buyAsset") val buyAsset: String = "",
    @SerialName("sellAmount") val sellAmount: String? = null,
    @SerialName("buyAmount") val buyAmount: String? = null,
)

/** SwapKit's per-leg time estimate (seconds) used to populate ETA labels in the swap UI. */
@Serializable
data class SwapKitEstimatedTime(
    @SerialName("inbound") val inbound: Double? = null,
    @SerialName("swap") val swap: Double? = null,
    @SerialName("outbound") val outbound: Double? = null,
    @SerialName("total") val total: Double? = null,
)

/** A fee entry returned alongside a route — gas, network, affiliate, etc. */
@Serializable
data class SwapKitFee(
    @SerialName("type") val type: String? = null,
    @SerialName("amount") val amount: String? = null,
    @SerialName("asset") val asset: String? = null,
    @SerialName("chain") val chain: String? = null,
    @SerialName("protocol") val protocol: String? = null,
)

/** Non-fatal advisory attached to a route (e.g. high price impact); surfaced in the verify UI. */
@Serializable
data class SwapKitWarning(
    @SerialName("code") val code: String? = null,
    @SerialName("display") val display: String? = null,
)

/** Route-level metadata. `tags` may include `FASTEST` / `RECOMMENDED` / `CHEAPEST` per V3 spec. */
@Serializable
data class SwapKitRouteMeta(
    @SerialName("assets") val assets: List<SwapKitMetaAsset> = emptyList(),
    @SerialName("tags") val tags: List<String> = emptyList(),
)

/** Asset metadata referenced from [SwapKitRouteMeta.assets]. */
@Serializable
data class SwapKitMetaAsset(
    @SerialName("asset") val asset: String? = null,
    @SerialName("price") val price: Double? = null,
    @SerialName("image") val image: String? = null,
)
