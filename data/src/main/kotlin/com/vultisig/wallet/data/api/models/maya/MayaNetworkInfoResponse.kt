package com.vultisig.wallet.data.api.models.maya

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Response from Midgard /v2/network endpoint
@Serializable
data class MayaNetworkInfoResponse(
    @SerialName("bondingAPY")
    val bondingAPY: String?,
    @SerialName("nextChurnHeight")
    val nextChurnHeight: String?,
    @SerialName("totalPooledRune")
    val totalPooledRune: String?,
    @SerialName("liquidityAPY")
    val liquidityAPY: String?,
)

// Response from Midgard /v2/health endpoint
@Serializable
data class MayaHealth(
    @SerialName("lastThorNode")
    val lastMayaNode: LastNodeInfo
) {
    @Serializable
    data class LastNodeInfo(
        val height: Long,
        val timestamp: Int
    )
}
