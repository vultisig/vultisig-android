package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmosIbcDenomTraceJson(
    @SerialName("denom_trace")
    val denomTrace: CosmosIbcDenomTraceDenomTraceJson?,
)

@Serializable
data class CosmosIbcDenomTraceDenomTraceJson(
    @SerialName("path")
    val path: String,
    @SerialName("base_denom")
    val baseDenom: String,
)