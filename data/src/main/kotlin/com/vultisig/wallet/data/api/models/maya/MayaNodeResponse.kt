package com.vultisig.wallet.data.api.models.maya

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MayaNodeResponse(
    @SerialName("node_address")
    val nodeAddress: String,
    @SerialName("status")
    val status: String,
    @SerialName("bond")
    val bond: String,
    @SerialName("bond_providers")
    val bondProviders: BondProvidersInfo,
    @SerialName("current_award")
    val currentAward: String
) {
    @Serializable
    data class BondProvidersInfo(
        @SerialName("node_operator_fee")
        val nodeOperatorFee: String,
        @SerialName("providers")
        val providers: List<BondProvider>
    )

    @Serializable
    data class BondProvider(
        @SerialName("bond_address")
        val bondAddress: String,
        @SerialName("bonded")
        val bonded: Boolean,
        @SerialName("reward")
        val reward: String,
        @SerialName("pools")
        val pools: Map<String, String> = emptyMap()
    )
}

@Serializable
data class MayaBondedNodesResponse(
    val totalBonded: String,
    val nodes: List<MayaBondNode>
)

@Serializable
data class MayaBondNode(
    val status: String,
    val address: String,
    val bond: String
) {
    val id: String
        get() = address

    val shortAddress: String
        get() = if (address.length > 4) {
            address.takeLast(4)
        } else {
            address
        }
}
