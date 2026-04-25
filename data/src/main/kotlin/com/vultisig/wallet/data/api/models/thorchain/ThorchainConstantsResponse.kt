package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThorchainConstantsResponse(@SerialName("int_64_values") val int64Values: Int64Values) {
    @Serializable
    data class Int64Values(
        @SerialName("MinRuneForTCYStakeDistribution")
        val minRuneForTCYStakeDistribution: Long? = null,
        @SerialName("MinTCYForTCYStakeDistribution")
        val minTcyForTCYStakeDistribution: Long? = null,
        @SerialName("TCYStakeSystemIncomeBps") val tcyStakeSystemIncomeBps: Long? = null,
    )
}
