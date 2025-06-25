package com.vultisig.wallet.data.api.models.cardano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CardanoUtxoResponseJson(
    @SerialName("tx_hash")
    val txHash: String? = null,
    @SerialName("tx_index")
    val txIndex: Long? = null,
    @SerialName("value")
    val value: String? = null,
)