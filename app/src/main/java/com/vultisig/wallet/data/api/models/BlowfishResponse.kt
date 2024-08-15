package com.vultisig.wallet.data.api.models

import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.mediator.Message

internal data class BlowfishResponse (
    @SerializedName("warnings")
    val warnings: List<BlowfishWarning>?,
    @SerializedName("aggregated")
    val aggregated: BlowfishAggregated?,
)

internal data class BlowfishAggregated (
    @SerializedName("warnings")
    val warnings: List<BlowfishWarning>?,
)

internal data class BlowfishWarning(
    @SerializedName("message")
    val message: String?,
)