package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response returned by `GET /providers` — a top-level JSON array per the SwapKit V3 docs. Each
 * [SwapKitProviderEntry] lists the SwapKit chain identifiers (e.g. "ETH", "BSC", "SOL") that a
 * sub-provider is currently routing on. The client unions these to derive the set of source chains
 * where SwapKit is eligible — cached 24h via SwapKitProviderCache to avoid hammering the endpoint.
 */
typealias SwapKitProvidersResponseJson = List<SwapKitProviderEntry>

/** A single sub-provider entry from `GET /providers` listing the SwapKit chain ids it routes on. */
@Serializable
data class SwapKitProviderEntry(
    @SerialName("provider") val provider: String = "",
    @SerialName("supportedChainIds") val supportedChainIds: List<String> = emptyList(),
)
