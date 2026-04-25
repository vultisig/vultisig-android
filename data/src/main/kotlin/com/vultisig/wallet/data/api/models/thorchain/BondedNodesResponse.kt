package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BondedNodesResponse(
    @SerialName("address") val address: String,
    @SerialName("nodes") val nodes: List<BondedNode>,
    @SerialName("totalBonded") val totalBonded: String,
)

@Serializable
data class BondedNode(
    @SerialName("address") val address: String,
    @SerialName("bond") val bond: String,
    @SerialName("status") val status: String,
)
