package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response envelope returned by `GET /providers`. Each [SwapKitProviderEntry] lists the SwapKit
 * chain identifiers (e.g. "ETH", "BSC", "SOL") that a sub-provider is currently routing on. The
 * client unions these to derive the set of source chains where SwapKit is eligible — cached 24h
 * via SwapKitProviderCache to avoid hammering the endpoint.
 */
@Serializable
data class SwapKitProvidersResponseJson(
    @SerialName("providers") val providers: List<SwapKitProviderEntry> = emptyList(),
)

@Serializable
data class SwapKitProviderEntry(
    @SerialName("provider") val provider: String = "",
    @SerialName("enabledChainIds") val enabledChainIds: List<String> = emptyList(),
)
