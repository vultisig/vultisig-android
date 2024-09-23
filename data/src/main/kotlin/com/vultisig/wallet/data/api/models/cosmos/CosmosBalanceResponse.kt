package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmosBalanceResponse(
    @SerialName("balances")
    val balances: List<CosmosBalance>?,
)