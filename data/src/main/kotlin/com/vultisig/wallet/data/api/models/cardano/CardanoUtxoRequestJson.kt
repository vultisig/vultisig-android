package com.vultisig.wallet.data.api.models.cardano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CardanoUtxoRequestJson(
    @SerialName("_addresses")
    val addresses: List<String>
)