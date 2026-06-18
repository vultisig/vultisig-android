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
    /**
     * User-chosen slippage tolerance in basis points (100 = 1%), or null for "Auto" — in which case
     * each provider keeps its own default ([DEFAULT_THORCHAIN_TOLERANCE_BPS] for THORChain/Maya,
     * 0.5% for 1inch, 1% for Kyber). Honoured only by providers that accept a quote-time slippage
     * override; LI.FI / SwapKit / Jupiter ignore it (server-side protection). See #4858.
     */
    val slippageBps: Int? = null,
)

/**
 * Two-shape return from a [SwapQuoteSource]: native protocols return a fully-realised [SwapQuote];
 * EVM aggregators return raw [EVMSwapQuoteJson] that the caller wraps with chain-specific gas math.
 */
sealed class SwapQuoteResult {
    data class Native(val quote: SwapQuote) : SwapQuoteResult()

    /**
     * EVM-shaped envelope. [subProvider] disambiguates SwapKit's downstream protocol (Chainflip /
     * NEAR / Garden); `null` for direct aggregators (1inch, Kyber, LiFi, Jupiter). [swapId] is the
     * SwapKit `/v3/swap` swap id, carried through so the resulting transaction can be gated on the
     * destination-leg `/track` settlement; `null` for direct aggregators.
     */
    data class Evm(
        val data: EVMSwapQuoteJson,
        val subProvider: String? = null,
        val swapId: String? = null,
    ) : SwapQuoteResult()

    // A future SwapKit non-EVM route (BTC / TON / ADA / TRON / SUI / ZEC) rides Native, since
    // SwapQuote.SwapKit is itself a SwapQuote — no dedicated result variant is needed.

    fun expectNative(provider: SwapProvider): SwapQuote =
        when (this) {
            is Native -> quote
            is Evm ->
                throw SwapException.UnkownSwapError("Expected Native quote for $provider, got Evm")
        }

    fun expectEvm(provider: SwapProvider): EVMSwapQuoteJson =
        when (this) {
            is Evm -> data
            is Native ->
                throw SwapException.UnkownSwapError("Expected Evm quote for $provider, got Native")
        }
}

/**
 * Minimum-output tolerance (basis points) sent on every THORChain/Maya quote request as
 * `tolerance_bps`. The node bakes a real `LIM` into the returned swap memo — `expected_amount_out ×
 * (1 − toleranceBps/10_000)` — protecting the user from adverse pool moves and front-running
 * between quote and execution. Without it the memo carries no limit (unbounded slippage). 100 bps
 * (1%) is a conservative default aligned with the streaming-upgrade threshold; there is no per-swap
 * user slippage control yet. Mirrors iOS `SwapService.defaultThorchainToleranceBps`.
 */
internal const val DEFAULT_THORCHAIN_TOLERANCE_BPS = 100

/** Common contract for every per-provider quote source. */
interface SwapQuoteSource {
    suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult
}

/** Hilt map key used by the @IntoMap registry of [SwapQuoteSource]s keyed by [SwapProvider]. */
@MapKey annotation class SwapProviderKey(val value: SwapProvider)
