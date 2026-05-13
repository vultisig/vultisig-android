package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** List of bonded nodes and total bond amount for an address. */
@Serializable
data class BondedNodesResponse(
    @SerialName("address") val address: String,
    @SerialName("nodes") val nodes: List<BondedNode>,
    @SerialName("totalBonded") val totalBonded: String,
)

/** A single bonded node with its address, bond amount, and status. */
@Serializable
data class BondedNode(
    @SerialName("address") val address: String,
    @SerialName("bond") val bond: String,
    @SerialName("status") val status: String,
)
