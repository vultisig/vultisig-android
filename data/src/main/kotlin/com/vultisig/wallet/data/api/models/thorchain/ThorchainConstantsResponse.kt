package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** THORChain on-chain constants response, containing governance parameter values. */
@Serializable
data class ThorchainConstantsResponse(@SerialName("int_64_values") val int64Values: Int64Values) {
    /** Integer governance parameters relevant to TCY staking thresholds and income. */
    @Serializable
    data class Int64Values(
        @SerialName("MinRuneForTCYStakeDistribution")
        val minRuneForTCYStakeDistribution: Long? = null,
        @SerialName("MinTCYForTCYStakeDistribution")
        val minTcyForTCYStakeDistribution: Long? = null,
        @SerialName("TCYStakeSystemIncomeBps") val tcyStakeSystemIncomeBps: Long? = null,
    )
}
