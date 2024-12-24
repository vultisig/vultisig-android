package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class LiQuestResponseJson(
    @SerialName("priceUSD")
    val priceUsd: String,
)