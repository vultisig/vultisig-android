package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmosTransactionBroadcastResponse(
    @SerialName("tx_response")
    val txResponse: CosmosTransactionBroadcastTx?,
)

@Serializable
data class CosmosTransactionBroadcastTx(
    @SerialName("txhash")
    val txHash: String?,
    @SerialName("code")
    val code: Int?,
)