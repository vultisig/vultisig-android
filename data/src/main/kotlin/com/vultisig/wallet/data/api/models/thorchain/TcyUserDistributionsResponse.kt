package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

/** TCY distribution history for a user, with an optional total across all events. */
@Serializable
data class TcyUserDistributionsResponse(
    /** Unsorted; Midgard sends null for an address that was never paid a distribution. */
    val distributions: List<TcyUserDistribution>? = null,
    val total: String? = null,
) {
    /**
     * A single TCY distribution event: [date] in unix seconds and [amount] in RUNE base units
     * (1e8), both as strings.
     */
    @Serializable data class TcyUserDistribution(val date: String, val amount: String)
}
