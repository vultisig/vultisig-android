package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Full node details from the THORChain node API, including status, award, and bond providers. */
@Serializable
data class NodeDetailsResponse(
    @SerialName("node_address") val nodeAddress: String,
    @SerialName("status") val status: String,
    @SerialName("current_award") val currentAward: String,
    @SerialName("bond_providers") val bondProviders: BondProviders,
)

/** Bond provider list and operator fee for a THORChain node. */
@Serializable
data class BondProviders(
    @SerialName("node_operator_fee") val nodeOperatorFee: String,
    @SerialName("providers") val providers: List<BondProvider>,
)

/** A single bond provider with their bonding address and bond amount. */
@Serializable
data class BondProvider(
    @SerialName("bond_address") val bondAddress: String,
    @SerialName("bond") val bond: String,
    /** Bond Yield payout share in basis points; THORNode omits it when unset (0). */
    @SerialName("bps") val bps: Int? = null,
)

/** [address]'s Bond Yield share on this node in basis points; null when it is not a provider. */
fun NodeDetailsResponse.payoutBps(address: String): Int? =
    bondProviders.providers
        .firstOrNull { it.bondAddress.equals(address, ignoreCase = true) }
        ?.let { it.bps ?: 0 }
