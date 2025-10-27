package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenValue

sealed class SwapPayload {

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

    data class EVM(
        val data: EVMSwapPayloadJson
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

//    data class Kyber(
//        val data: KyberSwapPayloadJson
//    ) : SwapPayload() {
//
//        override val srcToken: Coin
//            get() = data.fromCoin
//
//        override val dstToken: Coin
//            get() = data.toCoin
//
//        override val srcTokenValue: TokenValue
//            get() = TokenValue(
//                value = data.fromAmount,
//                token = srcToken,
//            )
//
//        override val dstTokenValue: TokenValue
//            get() = TokenValue(
//                value = data.toAmountDecimal
//                    .movePointRight(dstToken.decimal)
//                    .toBigInteger(),
//                token = dstToken,
//            )
//    }
}