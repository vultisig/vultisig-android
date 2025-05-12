package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.api.models.THORChainSwapQuote
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

sealed class SwapQuote {

    abstract val expectedDstValue: TokenValue
    abstract val fees: TokenValue
    abstract val expiredAt: Instant

    data class OneInch(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val expiredAt: Instant,
        val data: OneInchSwapQuoteJson,
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

    companion object {
        val expiredAfter = 1.minutes
    }

}