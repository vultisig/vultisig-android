package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

/** TCY distribution history for a user, with an optional total across all events. */
@Serializable
data class TcyUserDistributionsResponse(
    val distributions: List<TcyUserDistribution>,
    val total: String? = null,
) {
    /** A single TCY distribution event with its date and distributed amount. */
    @Serializable data class TcyUserDistribution(val date: String, val amount: String)
}
