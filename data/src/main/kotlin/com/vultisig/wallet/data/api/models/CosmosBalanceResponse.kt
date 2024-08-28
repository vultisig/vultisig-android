package com.vultisig.wallet.data.api.models

import com.google.gson.annotations.SerializedName

data class CosmosBalanceResponse(
    @SerializedName("balances")
    val balances: List<CosmosBalance>?,
)