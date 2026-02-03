package com.vultisig.wallet.data.api.models

import kotlinx.serialization.Contextual
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
data class SolanaMinimumBalanceForRentExemptionJson(
    @SerialName("result")
    @Contextual
    val result: BigInteger,
)

@Serializable
data class SolanaBalanceResultJson(
    @SerialName("value")
    @Contextual
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
data class BroadcastTransactionRespJson(
    @SerialName("error")
    @Serializable
    val error: JsonObject?,
    @SerialName("result")
    val result: String?,
)

@Serializable
data class SolanaFeeObjectJson(
    @SerialName("prioritizationFee")
    @Contextual
    val prioritizationFee: BigInteger,
    @SerialName("slot")
    @Contextual
    val slot: BigInteger,
)

@Serializable
data class SolanaFeeForMessageResponse(
    @SerialName("error")
    val error: JsonObject? = null,
    @SerialName("result")
    val result: SolanaFeeForMessageResult? = null,
)

@Serializable
data class SolanaFeeForMessageResult(
    @SerialName("value")
    @Contextual
    val value: BigInteger? = null,
)

@Serializable
data class SolanaSignatureStatusesResult(
    val value: List<SolanaSignatureStatus?> = emptyList(),
)

@Serializable
data class SolanaSignatureStatus(
    val confirmationStatus: String? = null,
)