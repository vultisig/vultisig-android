package com.vultisig.wallet.data.api.models.cosmos

import com.google.gson.annotations.SerializedName

data class CosmosTransactionBroadcastResponse(
    @SerializedName("tx_response")
    val txResponse: CosmosTransactionBroadcastTx?,
)

data class CosmosTransactionBroadcastTx(
    @SerializedName("txhash")
    val txHash: String?,
    @SerializedName("code")
    val code: Int?,
)