package com.vultisig.wallet.data.api.models.quotes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class EVMSwapQuoteDeserialized {
    data class Result(val data: EVMSwapQuoteJson) : EVMSwapQuoteDeserialized()

    data class Error(val error: String) : EVMSwapQuoteDeserialized()
}

@Serializable
data class EVMSwapQuoteJson(
    @SerialName("dstAmount") val dstAmount: String,
    @SerialName("tx") val tx: OneInchSwapTxJson,
    @SerialName("error") val error: String? = null,
)

@Serializable
data class OneInchQuoteJson(
    @SerialName("dstAmount") val dstAmount: String,
    @SerialName("gas") val gas: Long,
)

@Serializable
data class OneInchSwapTxJson(
    @SerialName("from") val from: String,
    @SerialName("to") val to: String,
    // ERC20 approval spender (allowance target). For 1inch/Kyber/LiFi the swap `to` is itself the
    // allowance target, so this stays null and callers fall back to `to`. SwapKit instead routes
    // through a dedicated token-transfer proxy that pulls the tokens — that proxy must be approved,
    // not the swap entry contract in `to`. Approving the wrong one reverts with
    // ERC20InsufficientAllowance. See SwapKitQuoteSource.buildEvmQuoteFromSwapKit.
    @SerialName("allowanceTarget") val allowanceTarget: String? = null,
    @SerialName("gas") val gas: Long,
    @SerialName("data") val data: String,
    @SerialName("value") val value: String,
    @SerialName("gasPrice") val gasPrice: String,
    @SerialName("swapFee") val swapFee: String = "",
    @SerialName("swapFeeTokenContract") val swapFeeTokenContract: String = "",
    // Coin context for `swapFee`, mirroring commondata `OneInchTransaction` fields 8-10
    // (swap_fee_chain / swap_fee_token_id / swap_fee_decimals). The amount alone is ambiguous:
    // different aggregators denominate the affiliate fee in different coins (src-native vs the
    // destination token). A consumer holding only the KeysignPayload — notably the co-signer in a
    // Secure Vault join flow, which has no live quote — needs the coin to render a correct fiat
    // value. `swapFeeChain` empty / `swapFeeDecimals` null means a sender that predates the proto
    // extension; consumers must fall back rather than guess. `swapFeeTokenContract` (above) doubles
    // as `swap_fee_token_id`; empty denotes the chain's native fee coin.
    @SerialName("swapFeeChain") val swapFeeChain: String = "",
    @SerialName("swapFeeDecimals") val swapFeeDecimals: Int? = null,
)

@Serializable
data class OneInchSwapQuoteErrorResponse(
    @SerialName("statusCode") val statusCode: Int,
    @SerialName("description") val description: String,
    @SerialName("error") val error: String,
) {
    override fun toString(): String {
        return "OneInchSwapQuoteErrorResponse(code=$statusCode, message='$description')"
    }
}
