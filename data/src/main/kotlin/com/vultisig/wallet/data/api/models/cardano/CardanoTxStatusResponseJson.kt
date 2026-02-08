package com.vultisig.wallet.data.api.models.cardano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CardanoTxStatusResponseJson(
    @Serializable
    @SerialName("tx_hash")
    val txHash: String,

    @Serializable
    @SerialName("num_confirmations")
    val numConfirmations: Int? = null
)