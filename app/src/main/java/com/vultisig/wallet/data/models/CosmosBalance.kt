package com.vultisig.wallet.data.models

import com.google.gson.annotations.SerializedName

internal data class CosmosBalance(
    @SerializedName("denom")
    val denom: String,
    @SerializedName("amount")
    val amount: String
)