package com.vultisig.wallet.data.models

import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.THORChainSwapPayload

internal sealed class SwapPayload {

    abstract val srcToken: Coin
    abstract val dstToken: Coin

    abstract val srcTokenValue: TokenValue
    abstract val dstTokenValue: TokenValue

    data class ThorChain(
        val data: THORChainSwapPayload
    ) : SwapPayload() {

        override val srcToken: Coin
            get() = data.fromCoin

        override val dstToken: Coin
            get() = data.toCoin

        override val srcTokenValue: TokenValue
            get() = TokenValue(
                value = data.fromAmount,
                token = srcToken,
            )

        override val dstTokenValue: TokenValue
            get() = TokenValue(
                value = data.toAmountDecimal
                    .movePointRight(dstToken.decimal)
                    .toBigInteger(),
                token = dstToken,
            )

    }

    data class MayaChain(
        val data: THORChainSwapPayload
    ) : SwapPayload() {

        override val srcToken: Coin
            get() = data.fromCoin

        override val dstToken: Coin
            get() = data.toCoin

        override val srcTokenValue: TokenValue
            get() = TokenValue(
                value = data.fromAmount,
                token = srcToken,
            )

        override val dstTokenValue: TokenValue
            get() = TokenValue(
                value = data.toAmountDecimal
                    .movePointRight(dstToken.decimal)
                    .toBigInteger(),
                token = dstToken,
            )

    }

    data class OneInch(
        val data: OneInchSwapPayloadJson
    ) : SwapPayload() {

        override val srcToken: Coin
            get() = data.fromCoin

        override val dstToken: Coin
            get() = data.toCoin

        override val srcTokenValue: TokenValue
            get() = TokenValue(
                value = data.fromAmount,
                token = srcToken,
            )

        override val dstTokenValue: TokenValue
            get() = TokenValue(
                value = data.toAmountDecimal
                    .movePointRight(dstToken.decimal)
                    .toBigInteger(),
                token = dstToken,
            )

    }

}