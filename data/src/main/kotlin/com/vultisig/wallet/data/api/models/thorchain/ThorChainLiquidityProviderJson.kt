package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThorChainLiquidityProviderJson(
    @SerialName("asset") val asset: String,
    @SerialName("rune_address") val runeAddress: String? = null,
    @SerialName("asset_address") val assetAddress: String? = null,
    @SerialName("units") val units: String = "0",
    @SerialName("pending_rune") val pendingRune: String = "0",
    @SerialName("pending_asset") val pendingAsset: String = "0",
    @SerialName("rune_deposit_value") val runeDepositValue: String = "0",
    @SerialName("asset_deposit_value") val assetDepositValue: String = "0",
    @SerialName("rune_redeem_value") val runeRedeemValue: String = "0",
    @SerialName("asset_redeem_value") val assetRedeemValue: String = "0",
    @SerialName("luvi_growth_pct") val luviGrowthPct: String? = null,
)
