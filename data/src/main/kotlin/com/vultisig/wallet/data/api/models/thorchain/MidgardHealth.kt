package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

@Serializable
data class MidgardHealth(val lastThorNode: HeightInfo) {
    @Serializable
    data class HeightInfo(
        val height: Long,
        val timestamp: Long, // seconds since epoch
    )
}
