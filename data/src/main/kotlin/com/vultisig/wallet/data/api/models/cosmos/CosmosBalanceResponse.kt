package com.vultisig.wallet.data.api.models.cosmos

import com.google.gson.annotations.SerializedName

data class CosmosBalanceResponse(
    @SerializedName("balances")
    val balances: List<CosmosBalance>?,
)