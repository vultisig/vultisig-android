package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of the Cosmos `/cosmos/tx/v1beta1/simulate` endpoint. Only [GasInfo.gasUsed] is read; it
 * drives the dynamic gas limit (`gasUsed × adjustment`) and, via the chain's gas price, the fee
 * amount.
 */
@Serializable
data class CosmosSimulateResponse(
    /** Gas-accounting block from the simulation, or `null` when the node omits it. */
    @SerialName("gas_info") val gasInfo: GasInfo? = null
) {
    /** Gas-accounting block from a simulation; `gas_used` is the gas the tx actually consumed. */
    @Serializable
    data class GasInfo(
        /** Gas the simulator was allowed to use (`gas_wanted`); unused by the fee path. */
        @SerialName("gas_wanted") val gasWanted: String? = null,
        /** Gas the simulated tx actually consumed (`gas_used`); drives the dynamic gas limit. */
        @SerialName("gas_used") val gasUsed: String? = null,
    )
}
