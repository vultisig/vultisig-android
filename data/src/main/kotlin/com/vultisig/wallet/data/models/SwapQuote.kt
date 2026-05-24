package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Instant

sealed class SwapQuote {

    abstract val expectedDstValue: TokenValue
    abstract val fees: TokenValue
    abstract val expiredAt: Instant

    data class OneInch(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val expiredAt: Instant,
        val data: EVMSwapQuoteJson,
        val provider: String,
    ) : SwapQuote()

    data class ThorChain(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val expiredAt: Instant,
        val recommendedMinTokenValue: TokenValue,
        val data: THORChainSwapQuote,
    ) : SwapQuote()

    data class MayaChain(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val expiredAt: Instant,
        val recommendedMinTokenValue: TokenValue,
        val data: THORChainSwapQuote,
    ) : SwapQuote()

    /**
     * SwapKit non-EVM-shaped quote — carries a fully-formed [SwapKitSwapPayloadJson] (the same
     * shape that round-trips via proto field 26). Phase 2+ consumers (TON / BTC PSBT / ADA / TRON /
     * SUI / ZEC signers) read the payload directly when building the KeysignPayload; no EVM-style
     * `tx` envelope to interpret. [subProvider] surfaces the route's downstream protocol
     * (Chainflip, NEAR Intents, Garden, …) for the verify-row label.
     */
    data class SwapKit(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val expiredAt: Instant,
        val data: SwapKitSwapPayloadJson,
        val subProvider: String?,
    ) : SwapQuote()

    companion object {
        val expiredAfter = 1.minutes
    }
}
