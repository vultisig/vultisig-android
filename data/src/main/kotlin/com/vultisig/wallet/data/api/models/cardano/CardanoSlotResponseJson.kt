package com.vultisig.wallet.data.api.models.cardano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CardanoSlotResponseJson(
    @SerialName("abs_slot")
    val absSlot: Long? = null,
)