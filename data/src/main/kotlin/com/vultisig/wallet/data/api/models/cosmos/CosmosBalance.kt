package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmosBalance(
    @SerialName("denom")
    val denom: String,
    @SerialName("amount")
    val amount: String
)