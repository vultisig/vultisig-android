package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A vault's record on one MayaChain pool, from `/mayachain/pool/{pool}/liquidity_provider/{addr}`.
 *
 * `pending_cacao` is in CACAO's own 1e10 base units; `pending_asset` is in MayaChain's 1e8 fixed
 * point whatever the asset's decimals.
 */
@Serializable
data class MayaLiquidityProviderJson(
    @SerialName("asset") val asset: String,
    @SerialName("cacao_address") val cacaoAddress: String? = null,
    @SerialName("asset_address") val assetAddress: String? = null,
    @SerialName("units") val units: String = "0",
    @SerialName("pending_cacao") val pendingCacao: String = "0",
    @SerialName("pending_asset") val pendingAsset: String = "0",
    // Inbound hash of the side that arrived first; only set while the add is half-open.
    @SerialName("pending_tx_id") val pendingTxId: String? = null,
    // Refund happens at `lastAddHeight + PendingLiquidityAgeLimit`.
    @SerialName("last_add_height") val lastAddHeight: Long? = null,
)
