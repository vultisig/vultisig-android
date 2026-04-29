package com.vultisig.wallet.data.repositories.swap

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

    data class Evm(val data: EVMSwapQuoteJson) : SwapQuoteResult()
}

/** Common contract for every per-provider quote source. */
interface SwapQuoteSource {
    suspend fun fetch(request: SwapQuoteRequest): SwapQuoteResult
}

/** Hilt map key used by the @IntoMap registry of [SwapQuoteSource]s keyed by [SwapProvider]. */
@MapKey annotation class SwapProviderKey(val value: SwapProvider)
