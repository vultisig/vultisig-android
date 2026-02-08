package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import java.math.BigInteger


@Serializable
data class RpcPayload(
    @SerialName("method")
    val method: String,
    @SerialName("params")
    val params: JsonArray,
    @SerialName("jsonrpc")
    val jsonrpc: String = "2.0",
    @SerialName("id")
    val id: Int = 1,
)


@Serializable
data class RpcResponse(
    @SerialName("id")
    val id: Int,
    @SerialName("result")
    val result: String?,
    @SerialName("error")
    val error: RpcError?,
)

@Serializable
data class EvmFeeHistoryJson(
    @SerialName("reward")
    val reward: List<List<String>>,
)

@Serializable
data class EvmFeeHistoryResponseJson(
    @SerialName("id")
    val id: Int,
    @SerialName("result")
    val result: EvmFeeHistoryJson,
    @SerialName("error")
    val error: RpcError?,
)

@Serializable
data class EvmBaseFeeJson(
    @SerialName("baseFeePerGas")
    val baseFeePerGas: String,
)

@Serializable
data class EvmTxStatusJson(
    @SerialName("status")
    val status: String,
)

@Serializable
data class EvmRpcResponseJson<T>(
    @SerialName("id")
    val id: Int,
    @SerialName("result")
    val result: T,
    @SerialName("error")
    val error: RpcError?,
)

@Serializable
data class RpcResponseJson(
    @SerialName("id")
    val id: Int,
    @SerialName("result")
    val result: RpcResponseResultJson?,
    @SerialName("error")
    val error: RpcError?,
)

@Serializable
data class RpcResponseResultJson(
    @SerialName("gas_limit")
    val gasLimit: String,
    @SerialName("gas_per_pubdata_limit")
    val gasPerPubdataLimit: String,
    @SerialName("max_fee_per_gas")
    val maxFeePerGas: String,
    @SerialName("max_priority_fee_per_gas")
    val maxPriorityFeePerGas: String
)


@Serializable
data class RpcError(
    @SerialName("code")
    val code: Int,
    @SerialName("message")
    val message: String,
)

@Serializable
data class ErrorSendTransactionJson(
    @SerialName("message")
    val message: String
)

@Serializable
data class SendTransactionJson(
    @SerialName("result")
    val result: String?,

    @SerialName("error")
    val error: ErrorSendTransactionJson?
)

data class ZkGasFee(
    val gasLimit: BigInteger,
    val gasPerPubdataLimit: BigInteger,
    val maxFeePerGas: BigInteger,
    val maxPriorityFeePerGas: BigInteger,
)