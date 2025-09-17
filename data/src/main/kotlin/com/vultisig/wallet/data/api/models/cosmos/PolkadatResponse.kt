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