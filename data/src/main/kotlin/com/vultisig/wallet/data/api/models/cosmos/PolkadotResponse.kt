package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger

@Serializable
data class PolkadotGetNonceJson(
    @SerialName("result")
    @Contextual
    val result: BigInteger,
)

@Serializable
data class PolkadotGetBlockHashJson(
    @SerialName("result")
    val result: String,
)

@Serializable
data class PolkadotGetRunTimeVersionJson(
    @SerialName("result")
    val result: PolkadotGetRunTimeVersionResultJson,
)

@Serializable
data class PolkadotGetRunTimeVersionResultJson(
    @SerialName("transactionVersion")
    @Contextual
    val transactionVersion: BigInteger,
    @SerialName("specVersion")
    @Contextual
    val specVersion: BigInteger,
)

@Serializable
data class PolkadotGetBlockHeaderJson(
    @SerialName("result")
    val result: PolkadotGetBlockHeaderNumberJson,
)

@Serializable
data class PolkadotGetBlockHeaderNumberJson(
    @SerialName("number")
    val number: String,
)

@Serializable
data class PolkadotBroadcastTransactionJson(
    @SerialName("result")
    val result: String?,
    @SerialName("error")
    val error: PolkadotBroadcastTransactionErrorJson?,
)

@Serializable
data class PolkadotBroadcastTransactionErrorJson(
    @SerialName("code")
    val code: Int,
)

@Serializable
data class PolkadotQueryInfoResponseJson(
    val result: QueryInfoPayload?,
) {
    @Serializable
    data class QueryInfoPayload(
        @SerialName("partialFee")
        val partialFee: String?,
    )
}

@Serializable
data class PolkadotExtrinsicResponseJson(
    @SerialName("code")
    val code: Int,
    @SerialName("message")
    val message: String,
    @SerialName("generated_at")
    val generatedAt: Long,
    @SerialName("data")
    val data: PolkadotExtrinsicDataJson? = null,
)

@Serializable
data class PolkadotExtrinsicDataJson(
    @SerialName("error")
    val polkadotErrorData: PolkadotErrorData? = null,
)


@Serializable
data class PolkadotErrorData(
    @SerialName("batch_index")
    val batchIndex: Int,
    @SerialName("doc")
    val doc: String,
    @SerialName("module")
    val module: String,
    @SerialName("name")
    val name: String,
    @SerialName("value")
    val value: String,
    @SerialName("version")
    val version: Int,
)

