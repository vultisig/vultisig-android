package com.vultisig.wallet.data.api.models.thorchain

import java.math.BigInteger
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThorChainPoolJson(
    // formatted as THOR.TCY
    @SerialName("asset") val asset: String,
    // asset price in usd with 8 decimals
    @Contextual @SerialName("asset_tor_price") val assetTorPrice: BigInteger,
)
