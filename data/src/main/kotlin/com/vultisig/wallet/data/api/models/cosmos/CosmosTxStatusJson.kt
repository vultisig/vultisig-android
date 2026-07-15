package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CosmosTxStatusJson(
    @SerialName("tx_response") val txResponse: TxResponse? = null,
    @SerialName("tx") val tx: JsonObject? = null,
)

@Serializable
data class TxResponse(
    @SerialName("height") val height: String,
    @SerialName("txhash") val txHash: String,
    // Cosmos LCD (gRPC-gateway) omits zero-value fields, so a successfully executed tx has no
    // `code` in the JSON. Default to 0 (success) so deserialization of a landed tx doesn't throw.
    @SerialName("code") val code: Int = 0,
    // The Cosmos LCD (gRPC-gateway) serializes these in snake_case; the previous camelCase
    // @SerialName values never matched, so rawLog/gasUsed/gasWanted always deserialized to null.
    @SerialName("raw_log") val rawLog: String? = null,
    @SerialName("gas_used") val gasUsed: String? = null,
    @SerialName("gas_wanted") val gasWanted: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
)
