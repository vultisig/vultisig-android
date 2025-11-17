package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DenomUnit(
    val denom: String?,
    val exponent: Int?
)

@Serializable
data class DenomMetadata(
    val base: String?,
    val symbol: String?,
    val display: String?,
    @SerialName("denom_units")
    val denomUnits: List<DenomUnit>?
)

@Serializable
data class MetadataResponse(
    val metadata: DenomMetadata?
)

@Serializable
data class MetadatasResponse(
    val metadatas: List<DenomMetadata>?
)
