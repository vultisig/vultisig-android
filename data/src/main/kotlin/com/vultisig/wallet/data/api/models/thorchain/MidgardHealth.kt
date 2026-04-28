package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

/** Midgard health response containing the latest THORNode block info. */
@Serializable
data class MidgardHealth(val lastThorNode: HeightInfo) {
    /** Block height and timestamp (seconds since epoch) of the last processed THORNode block. */
    @Serializable
    data class HeightInfo(
        val height: Long,
        val timestamp: Long, // seconds since epoch
    )
}
