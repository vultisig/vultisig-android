package com.vultisig.wallet.models.cosmos

import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.data.models.CosmosBalance

internal data class CosmosBalanceResponse(
    @SerializedName("balances")
    val balances: List<CosmosBalance>?,
)