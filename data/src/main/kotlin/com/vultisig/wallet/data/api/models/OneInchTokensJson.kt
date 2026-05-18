package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OneInchTokensJson(@SerialName("tokens") val tokens: Map<String, OneInchTokenJson>)

@Serializable
data class OneInchTokenJson(
    @SerialName("address") val address: String,
    @SerialName("symbol") val symbol: String,
    @SerialName("decimals") val decimals: Int,
    @SerialName("name") val name: String,
    @SerialName("logoURI") val logoURI: String?,
    @SerialName("eip2612") val eip2612: Boolean? = null,
    @SerialName("tags") val tags: List<String>? = null,
    @SerialName("providers") val providers: List<String>? = null,
)

/**
 * Allowlist signal used by EVM token auto-discovery: CoinGecko-listed tokens are the legitimacy
 * signal, replacing the empty-logo heuristic that was dropping legit small-caps. Mirrors the SDK
 * `findEvmCoins` resolver and iOS `OneInchToken.isCoinGeckoVerified`.
 */
val OneInchTokenJson.isCoinGeckoVerified: Boolean
    get() = providers?.contains("CoinGecko") == true
