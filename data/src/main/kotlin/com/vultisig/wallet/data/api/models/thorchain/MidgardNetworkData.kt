package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Network-level data from the Midgard API, including bonding APY and next churn block. */
@Serializable
data class MidgardNetworkData(
    @SerialName("bondingAPY") val bondingAPY: String,
    @SerialName("nextChurnHeight") val nextChurnHeight: String,
)
