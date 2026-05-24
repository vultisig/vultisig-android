package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import dagger.MapKey

/**
 * Single, polymorphic request used by every [SwapQuoteSource]. Each provider reads only what it
 * needs.
 */
data class SwapQuoteRequest(
    val srcToken: Coin,
    val dstToken: Coin,
    val tokenValue: TokenValue,
    val srcAddress: String = "",
    val dstAddress: String = "",
    val isAffiliate: Boolean = false,
    val bpsDiscount: Int = 0,
    val referralCode: String = "",
    val affiliateBps: Int = 0,
)

/**
 * Two-shape return from a [SwapQuoteSource]: native protocols return a fully-realised [SwapQuote];
 * EVM aggregators return raw [EVMSwapQuoteJson] that the caller wraps with chain-specific gas math.
 */
sealed class SwapQuoteResult {
    data class Native(val quote: SwapQuote) : SwapQuoteResult()

    /**
     * EVM-shaped quote envelope. [subProvider] is the route's sub-provider tag when an aggregator
     * routes through a downstream protocol (e.g. SwapKit → Chainflip / NEAR Intents / Garden /
     * Flashnet); `null` for direct aggregators (1inch, Kyber, LiFi, Jupiter). The UI label uses it
     * to disambiguate the verify screen.
     */
    data class Evm(val data: EVMSwapQuoteJson, val subProvider: String? = null) : SwapQuoteResult()

    /**
     * SwapKit non-EVM-shaped quote envelope. EVM and Solana SwapKit routes stay on [Evm] (their
     * /v3/swap shape matches OneInch's 1:1); BTC PSBT / TON / ADA / TRON / SUI / ZEC route shapes
     * flow through here so the payload builder can stash the bytes on a [SwapPayload.SwapKit] for
     * cross-device proto round-trip via `swapkitSwapPayload` field 26.
     */
    data class SwapKit(val quote: com.vultisig.wallet.data.models.SwapQuote.SwapKit) :
        SwapQuoteResult()

    fun expectNative(provider: SwapProvider): SwapQuote =
        when (this) {
            is Native -> quote
            is Evm ->
                throw SwapException.UnkownSwapError("Expected Native quote for $provider, got Evm")
            is SwapKit ->
                throw SwapException.UnkownSwapError(
                    "Expected Native quote for $provider, got SwapKit"
                )
        }

    fun expectEvm(provider: SwapProvider): EVMSwapQuoteJson =
        when (this) {
            is Evm -> data
            is Native ->
                throw SwapException.UnkownSwapError("Expected Evm quote for $provider, got Native")
            is SwapKit ->
                throw SwapException.UnkownSwapError("Expected Evm quote for $provider, got SwapKit")
        }
}

/** Common contract for every per-provider quote source. */
interface SwapQuoteSource {
    suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult
}

/** Hilt map key used by the @IntoMap registry of [SwapQuoteSource]s keyed by [SwapProvider]. */
@MapKey annotation class SwapProviderKey(val value: SwapProvider)
