package com.vultisig.wallet.data.api.models.cosmos

import com.google.gson.annotations.SerializedName

data class CosmosBalance(
    @SerializedName("denom")
    val denom: String,
    @SerializedName("amount")
    val amount: String
)