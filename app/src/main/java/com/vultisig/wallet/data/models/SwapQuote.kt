package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.models.swap.THORChainSwapQuote
import kotlin.time.Duration

internal sealed class SwapQuote {

    abstract val expectedDstValue: TokenValue
    abstract val fees: TokenValue
    abstract val estimatedTime: Duration?

    data class OneInch(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val estimatedTime: Duration?,
        val data: OneInchSwapQuoteJson,
    ) : SwapQuote()

    data class ThorChain(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        override val estimatedTime: Duration?,
        val data: THORChainSwapQuote,
    ) : SwapQuote()

}