package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CosmosEnvelopedTxResponse(
    @SerialName("tx_response") val txResponse: TxResponseBody
)

@Serializable
internal data class TxResponseBody(
    @SerialName("code") val code: Int?,
    @SerialName("codespace") val codeSpace: String?,
    @SerialName("raw_log") val rawLog: String?,
)
