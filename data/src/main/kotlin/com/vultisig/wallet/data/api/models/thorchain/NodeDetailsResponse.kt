package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NodeDetailsResponse(
    @SerialName("node_address") val nodeAddress: String,
    @SerialName("status") val status: String,
    @SerialName("current_award") val currentAward: String,
    @SerialName("bond_providers") val bondProviders: BondProviders,
)

@Serializable
data class BondProviders(
    @SerialName("node_operator_fee") val nodeOperatorFee: String,
    @SerialName("providers") val providers: List<BondProvider>,
)

@Serializable
data class BondProvider(
    @SerialName("bond_address") val bondAddress: String,
    @SerialName("bond") val bond: String,
)
