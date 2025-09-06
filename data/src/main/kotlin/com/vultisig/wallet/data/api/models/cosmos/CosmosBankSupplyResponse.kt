package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class CosmosBankSupplyResponse(
    @SerialName("supply")
    val supply: List<CosmosBalance>,
//    @SerialName("pagination")
//    val pagination: CosmosPagination? = null
)


@Serializable
data class CosmosBankToken(
    val denom: String,
    val amount: String,
    val metadata: CosmosTokenMetadata?
)

@Serializable
data class CosmosTokenMetadataResponse(
    @SerialName("metadata")
    val metadata: CosmosTokenMetadata?
)

@Serializable
data class CosmosTokenMetadataListResponse(
    @SerialName("metadatas")
    val metadatas: List<CosmosTokenMetadata> = emptyList()
)

@Serializable
data class CosmosTokenMetadata(
    @SerialName("base")
    val base: String,
    @SerialName("symbol")
    val symbol: String = "",
    @SerialName("display")
    val display: String = "",
    @SerialName("name")
    val name: String = "",
    @SerialName("description")
    val description: String = "",
    @SerialName("denom_units")
    val denomUnits: List<CosmosDenomUnit> = emptyList()
) {
    val decimals: Int
        get() = if (denomUnits.isNotEmpty() && display.isNotEmpty()) {
            denomUnits.find { it.denom == (symbol.ifEmpty { display }) }?.exponent ?: 6
        } else 6
}

@Serializable
data class CosmosDenomUnit(
    @SerialName("denom")
    val denom: String,
    @SerialName("exponent")
    val exponent: Int
)

