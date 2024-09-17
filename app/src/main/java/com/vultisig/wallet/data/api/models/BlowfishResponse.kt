package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class BlowfishResponse(
    @SerialName("warnings")
    val warnings: List<BlowfishWarning>?,
    @SerialName("aggregated")
    val aggregated: BlowfishAggregated?,
)

@Serializable
internal data class BlowfishAggregated(
    @SerialName("warnings")
    val warnings: List<BlowfishWarning>?,
)

@Serializable
internal data class BlowfishWarning(
    @SerialName("message")
    val message: String?,
)