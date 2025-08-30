package com.vultisig.wallet.data.api.models.quotes

import com.vultisig.wallet.data.api.models.KyberSwapRouteResponse
import com.vultisig.wallet.data.models.Chain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger
import kotlin.text.toLongOrNull


sealed class KyberSwapQuoteDeserialized {
    data class Result(val result: KyberSwapRouteResponse) : KyberSwapQuoteDeserialized()
    data class Error(val error: KyberSwapErrorResponse) : KyberSwapQuoteDeserialized()
}

@Serializable
data class KyberSwapErrorResponse(
//  val code: Int,
    val message: String,
)

@Serializable
data class KyberSwapQuoteJson(
    @SerialName("code")
    val code: Int,
    @SerialName("message")
    val message: String,
    @SerialName("data")
    var data: KyberSwapQuoteData,
    @SerialName("requestId")
    val requestId: String
)

fun KyberSwapQuoteJson.gasForChain(chain: Chain): Long {
    val baseGas = data.gas?.toLongOrNull() ?: 600_000L
    val gasMultiplierTimes10 = when (chain) {
        Chain.Ethereum, Chain.Arbitrum, Chain.Optimism, Chain.Base, Chain.Polygon, Chain.Avalanche, Chain.BscChain -> 20L
        else -> 16L
    }
    return (baseGas * gasMultiplierTimes10) / 10
}

val KyberSwapQuoteJson.tx: Transaction
    get() = Transaction(
        from = "",
        to = data.routerAddress,
        data = data.data,
        value = data.transactionValue,
        gasPrice = data.gasPrice ?: "",
        gas = data.gas?.toLongOrNull() ?: 0L,
        fee = data.fee?.toLong() ?: 0L
    )

var KyberSwapQuoteJson.dstAmount: String
    get() = data.amountOut
    set(value) {
        data = data.copy(amountOut = value)
    }

@Serializable
data class KyberSwapQuoteData(
    @SerialName("amountIn")
    val amountIn: String,
    @SerialName("amountInUsd")
    val amountInUsd: String,
    @SerialName("amountOut")
    val amountOut: String,
    @SerialName("amountOutUsd")
    val amountOutUsd: String,
    @SerialName("gas")
    val gas: String?,
    @SerialName("gasUsd")
    val gasUsd: String,
    @SerialName("data")
    val data: String,
    @SerialName("routerAddress")
    val routerAddress: String,
    @SerialName("transactionValue")
    val transactionValue: String,
    //not in response
    @kotlinx.serialization.Transient
    val gasPrice: String? = "",
    @kotlinx.serialization.Transient
    val fee: BigInteger? = BigInteger.ZERO,
)

data class Transaction(
    val from: String,
    val to: String,
    val data: String,
    val value: String,
    val gasPrice: String,
    val gas: Long,
    val fee: Long
)