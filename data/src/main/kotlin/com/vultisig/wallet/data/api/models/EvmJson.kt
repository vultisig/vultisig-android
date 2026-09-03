package com.vultisig.wallet.data.api.models

import java.math.BigInteger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
data class RpcPayload(
    @SerialName("method") val method: String,
    @SerialName("params") val params: JsonArray,
    @SerialName("jsonrpc") val jsonrpc: String = "2.0",
    @SerialName("id") val id: Int = 1,
)

@Serializable
data class RpcResponse(
    @SerialName("id") val id: Int,
    @SerialName("result") val result: String?,
    @SerialName("error") val error: RpcError?,
)

@Serializable data class EvmFeeHistoryJson(@SerialName("reward") val reward: List<List<String>>)

@Serializable
data class EvmFeeHistoryResponseJson(
    @SerialName("id") val id: Int,
    @SerialName("result") val result: EvmFeeHistoryJson,
    @SerialName("error") val error: RpcError?,
)

@Serializable data class EvmBaseFeeJson(@SerialName("baseFeePerGas") val baseFeePerGas: String)

/** EVM transaction receipt fields returned by `eth_getTransactionReceipt`. */
@Serializable
data class EvmTxStatusJson(
    @SerialName("status") val status: String,
    @SerialName("gasUsed") val gasUsed: String? = null,
    @SerialName("effectiveGasPrice") val effectiveGasPrice: String? = null,
)

@Serializable
data class EvmRpcResponseJson<T>(
    @SerialName("id") val id: Int,
    @SerialName("result") val result: T? = null,
    @SerialName("error") val error: RpcError? = null,
)

@Serializable
data class RpcResponseJson(
    @SerialName("id") val id: Int,
    @SerialName("result") val result: RpcResponseResultJson?,
    @SerialName("error") val error: RpcError?,
)

@Serializable
data class RpcResponseResultJson(
    @SerialName("gas_limit") val gasLimit: String,
    @SerialName("gas_per_pubdata_limit") val gasPerPubdataLimit: String,
    @SerialName("max_fee_per_gas") val maxFeePerGas: String,
    @SerialName("max_priority_fee_per_gas") val maxPriorityFeePerGas: String,
)

@Serializable
data class RpcError(@SerialName("code") val code: Int, @SerialName("message") val message: String)

/**
 * The subset of `eth_getTransactionByHash` needed to replay a mined transaction with `eth_call`.
 * Every field but `from` is optional: a contract creation has no `to`, and a node that omits
 * `blockNumber` is still reporting a pending transaction, which cannot be replayed at a block.
 */
@Serializable
data class EvmTxByHashJson(
    @SerialName("from") val from: String,
    @SerialName("to") val to: String? = null,
    @SerialName("input") val input: String? = null,
    @SerialName("value") val value: String? = null,
    @SerialName("gas") val gas: String? = null,
    @SerialName("blockNumber") val blockNumber: String? = null,
)

/**
 * An `eth_call` error, kept separate from [RpcError] because a revert's payload arrives under
 * `data` in a shape the node chooses — a hex string on most, a nested object on some — so it is
 * modelled as a raw [JsonElement] and narrowed at the point of decoding.
 */
@Serializable
data class EvmCallErrorJson(
    @SerialName("code") val code: Int? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("data") val data: JsonElement? = null,
)

@Serializable
data class EvmCallResponseJson(
    @SerialName("result") val result: String? = null,
    @SerialName("error") val error: EvmCallErrorJson? = null,
)

@Serializable data class ErrorSendTransactionJson(@SerialName("message") val message: String)

@Serializable
data class SendTransactionJson(
    @SerialName("result") val result: String?,
    @SerialName("error") val error: ErrorSendTransactionJson?,
)

data class ZkGasFee(
    val gasLimit: BigInteger,
    val gasPerPubdataLimit: BigInteger,
    val maxFeePerGas: BigInteger,
    val maxPriorityFeePerGas: BigInteger,
)
