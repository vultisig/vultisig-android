package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import java.math.BigDecimal
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Instant

sealed class SwapQuote {

    abstract val expectedDstValue: TokenValue
    abstract val fees: TokenValue
    abstract val expiredAt: Instant

    /**
     * Fractional price impact of the swap (e.g. `0.0133` == 1.33%), or null when the provider does
     * not expose it. THORChain/Maya derive it from the quote's `slippage_bps`; SwapKit from its
     * route's `totalSlippageBps`. EVM aggregators (1inch/Kyber/LiFi/Jupiter) do not report it, so
     * the Price Impact row is hidden for them (iOS parity).
     */
    open val priceImpact: BigDecimal?
        get() = null

    data class OneInch(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val expiredAt: Instant,
        val data: EVMSwapQuoteJson,
        val provider: String,
        // SwapKit routes through a sub-provider (Chainflip / NEAR / Garden). Carried on the quote —
        // not a transient local var — so the "via <sub-provider>" label survives a quote cache hit
        // instead of collapsing back to the generic "SwapKit". Null for 1inch / Kyber / LiFi.
        val subProvider: String? = null,
        // SwapKit `/v3/swap` swap id, carried onto the persisted swap so its tx-history Success can
        // be gated on the destination-leg `/track` settlement. Null for 1inch / Kyber / LiFi.
        val swapId: String? = null,
        // Populated for SwapKit (from its route's totalSlippageBps); null for 1inch/Kyber/LiFi/
        // Jupiter, which do not report price impact.
        override val priceImpact: BigDecimal? = null,
    ) : SwapQuote()

    data class ThorChain(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val expiredAt: Instant,
        val recommendedMinTokenValue: TokenValue,
        val data: THORChainSwapQuote,
    ) : SwapQuote() {
        override val priceImpact: BigDecimal?
            get() = data.slippageBps.toPriceImpactFraction()
    }

    data class MayaChain(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val expiredAt: Instant,
        val recommendedMinTokenValue: TokenValue,
        val data: THORChainSwapQuote,
    ) : SwapQuote() {
        override val priceImpact: BigDecimal?
            get() = data.slippageBps.toPriceImpactFraction()
    }

    /**
     * SwapKit non-EVM quote (BTC / TON / ADA / TRON / SUI / ZEC). Carries a fully-formed
     * [SwapKitSwapPayloadJson]; per-chain signers read it directly. [subProvider] drives the
     * verify-row label.
     */
    data class SwapKit(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val expiredAt: Instant,
        val data: SwapKitSwapPayloadJson,
        val subProvider: String?,
        override val priceImpact: BigDecimal? = null,
    ) : SwapQuote()

    companion object {
        val expiredAfter = 1.minutes
    }
}

/** Converts a basis-point slippage value (100 == 1%) into the fractional price impact (0.01). */
private fun Int?.toPriceImpactFraction(): BigDecimal? =
    this?.let { BigDecimal(it).movePointLeft(4) }
