package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenValue

sealed class SwapPayload {

    abstract val srcToken: Coin
    abstract val dstToken: Coin

    abstract val srcTokenValue: TokenValue
    abstract val dstTokenValue: TokenValue

    data class ThorChain(val data: THORChainSwapPayload) : SwapPayload() {

        override val srcToken: Coin
            get() = data.fromCoin

        override val dstToken: Coin
            get() = data.toCoin

        override val srcTokenValue: TokenValue
            get() = TokenValue(value = data.fromAmount, token = srcToken)

        override val dstTokenValue: TokenValue
            get() =
                TokenValue(
                    value = data.toAmountDecimal.movePointRight(dstToken.decimal).toBigInteger(),
                    token = dstToken,
                )
    }

    data class MayaChain(val data: THORChainSwapPayload) : SwapPayload() {

        override val srcToken: Coin
            get() = data.fromCoin

        override val dstToken: Coin
            get() = data.toCoin

        override val srcTokenValue: TokenValue
            get() = TokenValue(value = data.fromAmount, token = srcToken)

        override val dstTokenValue: TokenValue
            get() =
                TokenValue(
                    value = data.toAmountDecimal.movePointRight(dstToken.decimal).toBigInteger(),
                    token = dstToken,
                )
    }

    data class EVM(val data: EVMSwapPayloadJson) : SwapPayload() {

        override val srcToken: Coin
            get() = data.fromCoin

        override val dstToken: Coin
            get() = data.toCoin

        override val srcTokenValue: TokenValue
            get() = TokenValue(value = data.fromAmount, token = srcToken)

        override val dstTokenValue: TokenValue
            get() =
                TokenValue(
                    value = data.toAmountDecimal.movePointRight(dstToken.decimal).toBigInteger(),
                    token = dstToken,
                )
    }

    /**
     * SwapKit-routed swaps whose wire shape doesn't fit [EVM] / [EVMSwapPayloadJson]. EVM and
     * Solana SwapKit routes continue to ride [EVM] (their /v3/swap shape matches OneInchQuote 1:1
     * and stays typed at the proto layer with `provider = "swapkit"`). This variant carries the
     * non-EVM shapes that round-trip via the `swapkit_swap_payload` proto field 26: BTC PSBT, TON
     * transfer arrays, ADA CBOR, TRON TronWeb objects, SUI PTB, ZEC Sapling-v4 PSBT, etc. The
     * per-chain dispatcher reads [SwapKitSwapPayloadJson.txType] to pick the right signer.
     */
    data class SwapKit(val data: SwapKitSwapPayloadJson) : SwapPayload() {

        override val srcToken: Coin
            get() = data.fromCoin

        override val dstToken: Coin
            get() = data.toCoin

        override val srcTokenValue: TokenValue
            get() = TokenValue(value = data.fromAmount, token = srcToken)

        override val dstTokenValue: TokenValue
            get() =
                TokenValue(
                    value = data.toAmountDecimal.movePointRight(dstToken.decimal).toBigInteger(),
                    token = dstToken,
                )
    }
}
