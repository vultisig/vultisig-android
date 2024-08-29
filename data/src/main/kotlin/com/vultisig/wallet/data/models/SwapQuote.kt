package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.api.models.THORChainSwapQuote

sealed class SwapQuote {

    abstract val expectedDstValue: TokenValue
    abstract val fees: TokenValue

    data class OneInch(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        val data: OneInchSwapQuoteJson,
    ) : SwapQuote()

    data class ThorChain(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        val recommendedMinTokenValue: TokenValue,
        val data: THORChainSwapQuote,
    ) : SwapQuote()

    data class MayaChain(
        override val expectedDstValue: TokenValue,
        override val fees: TokenValue,
        val recommendedMinTokenValue: TokenValue,
        val data: THORChainSwapQuote,
    ) : SwapQuote()

}