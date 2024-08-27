package com.vultisig.wallet.models.cosmos

import com.google.gson.annotations.SerializedName

internal data class CosmosTransactionBroadcastResponse(
    @SerializedName("tx_response") val txResponse: CosmosTransactionBroadcastTx?,
)

internal data class CosmosTransactionBroadcastTx(
    @SerializedName("txhash")
    val txHash: String?,
    @SerializedName("code")
    val code: Int?,
)