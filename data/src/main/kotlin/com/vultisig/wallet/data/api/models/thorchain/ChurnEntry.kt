package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChurnEntry(
    @SerialName("date") val date: String,
    @SerialName("height") val height: String,
)
