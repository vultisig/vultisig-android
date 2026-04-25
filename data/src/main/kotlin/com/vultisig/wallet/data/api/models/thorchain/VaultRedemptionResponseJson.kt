package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VaultRedemptionResponseJson(@SerialName("data") val data: VaultRedemptionDataJson)

@Serializable
data class VaultRedemptionDataJson(
    @SerialName("shares") val shares: String = "",
    @SerialName("nav") val nav: String = "",
    @SerialName("nav_per_share") val navPerShare: String = "",
    @SerialName("liquid_bond_shares") val liquidBondShares: String = "",
    @SerialName("liquid_bond_size") val liquidBondSize: String = "",
)
