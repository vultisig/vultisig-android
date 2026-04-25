package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TcyStakersResponse(@SerialName("tcy_stakers") val tcyStakers: List<TcyStaker>) {
    @Serializable data class TcyStaker(val address: String, val amount: String)
}
