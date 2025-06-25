package com.vultisig.wallet.data.api.models.cardano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CardanoBroadcastDataResponseJson(
    @SerialName("transaction_hash")
    val transactionHash: String,
)