package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for SwapKit `POST /track`. Keys off the on-chain broadcast [hash] plus the source
 * [chainId] (decimal EIP-155 id for EVM, slug for non-EVM — see
 * [com.vultisig.wallet.data.api.txstatus.SwapKitChainIdentifier]). `/track` does NOT key off the
 * `swapId`. Mirrors iOS' `SwapKitTrackRequest`.
 */
@Serializable
data class SwapKitTrackRequest(
    @SerialName("hash") val hash: String,
    @SerialName("chainId") val chainId: String,
)

/**
 * Decodable model for SwapKit `POST /track`. Surfaces both the coarse [status] (7-value
 * `TxnStatus`) and the fine-grained [trackingStatus] (14-value); the mapper prefers
 * [trackingStatus] when present since it conveys destination-leg progress the coarse field
 * collapses. Mirrors iOS' `SwapKitTrackingResponse`.
 */
@Serializable
data class SwapKitTrackResponseJson(
    @SerialName("chainId") val chainId: String? = null,
    @SerialName("hash") val hash: String? = null,
    @SerialName("block") val block: Long? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("trackingStatus") val trackingStatus: String? = null,
    @SerialName("fromAsset") val fromAsset: String? = null,
    @SerialName("fromAmount") val fromAmount: String? = null,
    @SerialName("fromAddress") val fromAddress: String? = null,
    @SerialName("toAsset") val toAsset: String? = null,
    @SerialName("toAmount") val toAmount: String? = null,
    @SerialName("toAddress") val toAddress: String? = null,
    @SerialName("finalisedAt") val finalisedAt: Double? = null,
)
