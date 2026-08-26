package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response returned by `GET /providers` — a top-level JSON array per the SwapKit V3 docs. Each
 * [SwapKitProviderEntry] carries two chain-id lists, and the difference between them matters: a
 * chain can sit in `supportedChainIds` while never appearing in `enabledChainIds` (observed live
 * for `hype` and `stellar`, and for every chain of a provider that is currently dark). Enablement
 * is what the client gates on — cached 24h via SwapKitProviderCache to avoid hammering the
 * endpoint.
 */
typealias SwapKitProvidersResponseJson = List<SwapKitProviderEntry>

/** A single sub-provider entry from `GET /providers` listing the SwapKit chain ids it routes on. */
@Serializable
data class SwapKitProviderEntry(
    @SerialName("provider") val provider: String = "",
    /** Chains this sub-provider is routing on right now. The eligibility signal. */
    @SerialName("enabledChainIds") val enabledChainIds: List<String> = emptyList(),
    /** Chains the sub-provider knows about — a superset of [enabledChainIds]. Not an offer. */
    @SerialName("supportedChainIds") val supportedChainIds: List<String> = emptyList(),
)
