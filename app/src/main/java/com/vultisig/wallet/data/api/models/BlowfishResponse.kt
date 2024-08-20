package com.vultisig.wallet.data.api.models

import com.google.gson.annotations.SerializedName

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