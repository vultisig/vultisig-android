package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** List of all active TCY stakers returned by the THORChain API. */
@Serializable
data class TcyStakersResponse(@SerialName("tcy_stakers") val tcyStakers: List<TcyStaker>) {
    /** A single TCY staker with their address and staked amount. */
    @Serializable data class TcyStaker(val address: String, val amount: String)
}
