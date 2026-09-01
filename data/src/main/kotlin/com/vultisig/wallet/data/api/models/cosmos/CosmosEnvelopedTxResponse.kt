package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CosmosEnvelopedTxResponse(
    @SerialName("tx_response") val txResponse: TxResponseBody,
    @SerialName("tx") val tx: CosmosTx? = null,
)

@Serializable
internal data class TxResponseBody(
    @SerialName("code") val code: Int?,
    @SerialName("codespace") val codeSpace: String?,
    @SerialName("raw_log") val rawLog: String?,
)

@Serializable internal data class CosmosTx(@SerialName("body") val body: CosmosTxBody? = null)

@Serializable
internal data class CosmosTxBody(
    @SerialName("memo") val memo: String? = null,
    @SerialName("messages") val messages: List<CosmosTxMessage> = emptyList(),
)

@Serializable
internal data class CosmosTxMessage(
    @SerialName("@type") val type: String? = null,
    @SerialName("memo") val memo: String? = null,
)
