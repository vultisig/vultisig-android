package com.vultisig.wallet.data.api.models

import com.vultisig.wallet.data.utils.BigIntegerSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.math.BigInteger


@Serializable
data class SolanaBalanceJson(
    @SerialName("error")
    val error: String?,
    @SerialName("result")
    val result: SolanaBalanceResultJson?,
)

@Serializable
data class SolanaBalanceResultJson(
    @SerialName("value")
    @Serializable(BigIntegerSerializer::class)
    val value: BigInteger,
)

@Serializable
data class RecentBlockHashResponseJson(
    @SerialName("error")
    val error: String? = null,
    @SerialName("result")
    val result: RecentBlockHashResultJson?,
)

@Serializable
data class RecentBlockHashResultJson(
    @SerialName("value")
    val value: RecentBlockHashValueJson ,
)

@Serializable
data class RecentBlockHashValueJson(
    @SerialName("blockhash")
    val blockHash: String,
)

@Serializable
data class SolanaFeeObjectRespJson(
    @SerialName("error")
    val error: String?,
    @SerialName("result")
    val result: List<SolanaFeeObjectJson>?,
)


@Serializable
data class SolanaRpcError(
    @SerialName("code")
    val code: Int,
    @SerialName("message")
    val message: String,
    @SerialName("data")
    val data: RpcErrorData,
)
@Serializable
data class RpcErrorData(
    @SerialName("accounts")
    val accounts: String?,
    @SerialName("err")
    val err: String,
    @SerialName("innerInstructions")
    val innerInstructions: String?,
    @SerialName("logs")
    val logs: List<String>,
    @SerialName("returnData")
    val returnData: String?,
    @SerialName("unitsConsumed")
    val unitsConsumed: Int,
)
@Serializable
data class BroadcastTransactionRespJson(
    @SerialName("error")
    @Serializable
    val error: SolanaRpcError?,
    @SerialName("result")
    val result: String?,
)

@Serializable
data class SolanaFeeObjectJson(
    @SerialName("prioritizationFee")
    @Serializable(BigIntegerSerializer::class)
    val prioritizationFee: BigInteger,
    @SerialName("slot")
    @Serializable(BigIntegerSerializer::class)
    val slot: BigInteger,
)
