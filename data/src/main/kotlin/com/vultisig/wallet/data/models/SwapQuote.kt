package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

sealed class SwapQuote {

    abstract val expectedDstValue: TokenValue
    abstract val fees: TokenValue
    abstract val expiredAt: Instant

    data class Kyber(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val expiredAt: Instant,
        val data: KyberSwapQuoteJson,
    ) : SwapQuote()

    data class OneInch(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val expiredAt: Instant,
        val data: EVMSwapQuoteJson,
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