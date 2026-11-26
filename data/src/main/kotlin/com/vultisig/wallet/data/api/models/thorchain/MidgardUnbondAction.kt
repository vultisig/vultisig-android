package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The most actions one Midgard `/v2/actions` page returns. */
const val MIDGARD_ACTIONS_PAGE_LIMIT = 50

/** Without a default: a reply that leaves `actions` out says nothing, not "no unbonds". */
@Serializable
internal data class MidgardUnbondActionsResponse(
    @SerialName("actions") val actions: List<MidgardUnbondAction>
)

/** One `UNBOND` from a node, as THORChain and MayaChain Midgard both report it. */
@Serializable
data class MidgardUnbondAction(
    @SerialName("height") val height: String,
    @SerialName("in") val inputs: List<MidgardActionInput> = emptyList(),
    @SerialName("metadata") val metadata: MidgardUnbondMetadata? = null,
) {
    /** The memo the unbond was sent with, null when Midgard did not say. */
    val memo: String?
        get() = metadata?.bond?.memo

    /** The address that sent the unbond, the provider itself or the node operator. */
    val sender: String?
        get() = inputs.firstOrNull()?.address
}

@Serializable data class MidgardActionInput(@SerialName("address") val address: String? = null)

@Serializable
data class MidgardUnbondMetadata(@SerialName("bond") val bond: MidgardBondMetadata? = null)

@Serializable data class MidgardBondMetadata(@SerialName("memo") val memo: String? = null)
