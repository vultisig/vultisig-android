package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MidgardNetworkData(
    @SerialName("bondingAPY") val bondingAPY: String,
    @SerialName("nextChurnHeight") val nextChurnHeight: String,
)
