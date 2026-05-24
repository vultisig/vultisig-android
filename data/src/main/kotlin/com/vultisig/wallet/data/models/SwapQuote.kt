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
    ) : SwapQuote()

    companion object {
        val expiredAfter = 1.minutes
    }
}
