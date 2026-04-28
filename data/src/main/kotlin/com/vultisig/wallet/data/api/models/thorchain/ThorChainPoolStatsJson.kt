package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThorChainPoolStatsJson(
    @SerialName("asset") val asset: String,
    @SerialName("status") val status: String,
    @SerialName("annualPercentageRate") val annualPercentageRate: String? = null,
    @SerialName("poolAPY") val poolApy: String? = null,
    @SerialName("assetDepth") val assetDepth: String? = null,
    @SerialName("runeDepth") val runeDepth: String? = null,
)
