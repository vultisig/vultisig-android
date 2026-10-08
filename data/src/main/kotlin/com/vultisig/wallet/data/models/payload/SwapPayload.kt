package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue

sealed class SwapPayload {

    abstract val srcToken: Coin
    abstract val dstToken: Coin

    abstract val srcTokenValue: TokenValue
    abstract val dstTokenValue: TokenValue

    /**
     * Throws unless a swap (any provider, every chain) sells exactly [signingCoin]: same chain,
     * native flag, contract, ticker and decimals (co-signer reviews render the payload coin's
     * ticker and decimals). Contracts compare case-insensitively only on EVM chains, where they are
     * hex; every other chain (Solana base58 mints, …) compares them exactly. Each signer builds for
     * the signing coin while co-signers display the payload's coin. Mirrors vultisig-sdk's
     * `assertKeysignSwapSellsSigningCoin` and iOS `SwapPayload.requireSellsSigningCoin`.
     */
    fun requireSellsSigningCoin(signingCoin: Coin) {
        val isEvm = signingCoin.chain.standard == TokenStandard.EVM
        val mismatch =
            when {
                srcToken.chain != signingCoin.chain ->
                    "chain differs (${srcToken.chain.raw} vs ${signingCoin.chain.raw})"
                srcToken.isNativeToken != signingCoin.isNativeToken ->
                    "native flag differs (${srcToken.isNativeToken} vs ${signingCoin.isNativeToken})"
                !srcToken.contractAddress.equals(signingCoin.contractAddress, ignoreCase = isEvm) ->
                    "contract differs (${srcToken.contractAddress} vs ${signingCoin.contractAddress})"
                srcToken.ticker != signingCoin.ticker ->
                    "ticker differs (${srcToken.ticker} vs ${signingCoin.ticker})"
                srcToken.decimal != signingCoin.decimal ->
                    "decimals differ (${srcToken.decimal} vs ${signingCoin.decimal})"
                else -> null
            }
        require(mismatch == null) {
            "Swap sells ${srcToken.ticker} on ${srcToken.chain.raw} but signs " +
                "${signingCoin.ticker} on ${signingCoin.chain.raw}: $mismatch"
        }
    }

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
     * SwapKit routes whose wire shape doesn't fit [EVM]. EVM and Solana SwapKit still ride [EVM]
     * with `provider = "swapkit"`; this carries BTC PSBT / TON / ADA / TRON / SUI / ZEC.
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
