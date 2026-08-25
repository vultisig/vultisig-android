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
    // Inbound hash of the side that arrived first. Only set while the deposit is still half-open,
    // so it doubles as the marker that this record is a pending symmetric add rather than a
    // position.
    @SerialName("pending_tx_id") val pendingTxId: String? = null,
    // Block the pending side was recorded at. Refund happens at
    // `lastAddHeight + PendingLiquidityAgeLimit`, so the countdown cannot be derived without it.
    @SerialName("last_add_height") val lastAddHeight: Long? = null,
    @SerialName("rune_deposit_value") val runeDepositValue: String = "0",
    @SerialName("asset_deposit_value") val assetDepositValue: String = "0",
    @SerialName("rune_redeem_value") val runeRedeemValue: String = "0",
    @SerialName("asset_redeem_value") val assetRedeemValue: String = "0",
    @SerialName("luvi_growth_pct") val luviGrowthPct: String? = null,
)
