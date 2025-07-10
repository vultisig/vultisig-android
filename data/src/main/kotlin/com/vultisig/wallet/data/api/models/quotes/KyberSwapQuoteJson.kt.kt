package com.vultisig.wallet.data.api.models.quotes

import com.vultisig.wallet.data.models.Chain
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger
import kotlin.text.toLongOrNull

sealed class KyberSwapQuoteDeserialized {
    data class Result(val data: KyberSwapQuoteResponse) : KyberSwapQuoteDeserialized()
    data class Error(val error: String) : KyberSwapQuoteDeserialized()
}

@Serializable
data class KyberSwapQuoteJson(
    @SerialName("dstAmount")
    val dstAmount: String = "",
    @SerialName("tx")
    val tx: KyberSwapTxJson,
    @SerialName("error")
    val error: String? = null,
    @Contextual
    val fee: BigInteger? = null,
)

private fun calculateGasForChain(baseGas: Long, chain: Chain): Long {
    val gasMultiplierTimes10 = when (chain) {
        Chain.Ethereum -> 14L
        Chain.Arbitrum, Chain.Optimism, Chain.Base, Chain.Polygon, Chain.Avalanche, Chain.BscChain -> 20L
        else -> 16L
    }
    return (baseGas * gasMultiplierTimes10) / 10
}

fun KyberSwapQuoteJson.gasForChain(chain: Chain): Long {
    return calculateGasForChain(tx.gas, chain)
}

@Serializable
data class KyberSwapTxJson(
    @SerialName("from")
    val from: String,
    @SerialName("to")
    val to: String,
    @SerialName("data")
    val data: String,
    @SerialName("value")
    val value: String,
    @SerialName("gasPrice")
    val gasPrice: String,
    @SerialName("gas")
    val gas: Long
)


@Serializable
data class KyberSwapQuoteResponse(
    @SerialName("code")
    val code: Int,
    @SerialName("message")
    val message: String,
    @SerialName("data")
    var data: KyberSwapQuoteData,
    @SerialName("requestId")
    val requestId: String
) {
    var dstAmount: String
        get() = data.amountOut
        set(value) {
            // Note: This will only work if amountOut in KyberSwapQuoteData is a var, not val
            // Otherwise you'll need to handle this differently
            data = data.copy(amountOut = value)
        }

    fun gasForChain(chain: Chain): Long {
        val baseGas = data.gas?.toLongOrNull() ?: 600_000L
        val gasMultiplierTimes10 = when (chain) {
            Chain.Ethereum -> 14L
            Chain.Arbitrum, Chain.Optimism, Chain.Base, Chain.Polygon, Chain.Avalanche, Chain.BscChain -> 20L
            else -> 16L
        }
        return (baseGas * gasMultiplierTimes10) / 10
    }

    val tx: Transaction
        get() {
            return Transaction(
                from = "",
                to = data.routerAddress,
                data = data.data,
                value = data.transactionValue,
                gasPrice = data.gasPrice ?: "",
                gas = data.gas?.toLongOrNull() ?: 0L,
                fee = data.fee?.toLong() ?: 0L
            )
        }

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