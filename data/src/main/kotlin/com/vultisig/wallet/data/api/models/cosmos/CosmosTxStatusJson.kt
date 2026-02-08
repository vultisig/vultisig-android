package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CosmosTxStatusJson(
    @SerialName("tx_response")
    val txResponse: TxResponse? = null,
    @SerialName("tx")
    val tx: JsonObject? = null
)

@Serializable
data class TxResponse(
    @SerialName("height")
    val height: String,
    @SerialName("txhash")
    val txHash: String,
    @SerialName("code")
    val code: Int,
    @SerialName("rawLog")
    val rawLog: String? = null,
    @SerialName("gasUsed")
    val gasUsed: String? = null,
    @SerialName("gasWanted")
    val gasWanted: String? = null,
    @SerialName("timestamp")
    val timestamp: String? = null
)