package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.models.Coin
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Keysign payload for a SwapKit-sourced swap. Mirrors
 * [com.vultisig.wallet.data.models .EVMSwapPayloadJson] so the existing aggregator signing path can
 * be reused, but is kept as a distinct type so the proto round-trip can route SwapKit quotes back
 * to [SwapPayload.SwapKit] (and a downstream UI can render the sub-provider tag, e.g. "via
 * Chainflip").
 *
 * @property provider Top-level provider id — always `"SwapKit"`. Drives proto-deserializer routing
 *   back into this variant.
 * @property subProvider Optional sub-provider id from SwapKit's quote (`"chainflip"`, `"near"`, …);
 *   plumbed through for the verify-screen tag landing in a follow-up.
 */
data class SwapKitPayloadJson(
    val fromCoin: Coin,
    val toCoin: Coin,
    val fromAmount: BigInteger,
    val toAmountDecimal: BigDecimal,
    val quote: EVMSwapQuoteJson,
    val provider: String,
    val subProvider: String? = null,
)
