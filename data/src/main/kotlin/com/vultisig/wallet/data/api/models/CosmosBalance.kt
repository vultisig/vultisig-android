package com.vultisig.wallet.data.api.models

import com.google.gson.annotations.SerializedName

data class CosmosBalance(
    @SerializedName("denom")
    val denom: String,
    @SerializedName("amount")
    val amount: String
)