package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single churn event entry with its date and block height. */
@Serializable
data class ChurnEntry(
    @SerialName("date") val date: String,
    @SerialName("height") val height: String,
)
