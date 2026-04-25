package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** THORName owner metadata including affiliated addresses and referral configuration. */
@Serializable
data class ThorOwnerData(
    @SerialName("name") val name: String,
    @SerialName("expire_block_height") val expireBlockHeight: Long,
    @SerialName("owner") val owner: String,
    @SerialName("preferred_asset") val preferredAsset: String,
    @SerialName("preferred_asset_swap_threshold_rune") val preferredAssetSwapThresholdRune: String,
    @SerialName("affiliate_collector_rune") val affiliateCollectorRune: String,
    @SerialName("aliases") val aliases: List<Aliases> = emptyList(),
) {
    /** A chain-specific alias address registered under a THORName. */
    @Serializable data class Aliases(val chain: String, val address: String)
}
