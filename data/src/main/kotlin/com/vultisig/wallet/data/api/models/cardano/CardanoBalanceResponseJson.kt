package com.vultisig.wallet.data.api.models.cardano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CardanoBalanceResponseJson(
    @SerialName("balance")
    val balance: String? = null,
)