package com.vultisig.wallet.data.api.models.cosmos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of the Cosmos `/cosmos/tx/v1beta1/simulate` endpoint. Only [GasInfo.gasUsed] is read; it
 * drives the dynamic gas limit (`gasUsed × adjustment`) and, via the chain's gas price, the fee
 * amount.
 */
@Serializable
data class CosmosSimulateResponse(@SerialName("gas_info") val gasInfo: GasInfo? = null) {
    /** Gas-accounting block from a simulation; `gas_used` is the gas the tx actually consumed. */
    @Serializable
    data class GasInfo(
        @SerialName("gas_wanted") val gasWanted: String? = null,
        @SerialName("gas_used") val gasUsed: String? = null,
    )
}
