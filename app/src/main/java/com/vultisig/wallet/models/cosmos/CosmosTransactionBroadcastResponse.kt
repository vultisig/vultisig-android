package com.vultisig.wallet.models.cosmos

import com.google.gson.annotations.SerializedName

data class CosmosTransactionBroadcastResponse(
    @SerializedName("tx_response") val txResponse: CosmosTransactionBroadcastTx?,
)

data class CosmosTransactionBroadcastTx(
    val txhash: String?,
    val code: Int?,
)