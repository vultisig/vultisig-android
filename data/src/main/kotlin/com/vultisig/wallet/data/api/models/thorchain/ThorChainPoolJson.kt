package com.vultisig.wallet.data.api.models.thorchain

import java.math.BigInteger
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** THORChain pool entry with asset identifier and its USD price in TOR with 8 decimals. */
@Serializable
data class ThorChainPoolJson(
    // formatted as THOR.TCY
    @SerialName("asset") val asset: String,
    // asset price in usd with 8 decimals
    @Contextual @SerialName("asset_tor_price") val assetTorPrice: BigInteger,
    // pool status — typically Available / Staged / Suspended; absent on older endpoints
    @SerialName("status") val status: String? = null,
    // Sum of every half-finished symmetric add on this pool, 1e8. Non-zero means at least one
    // liquidity provider is waiting on a matching deposit — which pools are worth a per-user
    // lookup, without asking thornode about all ~100 of them.
    @SerialName("pending_inbound_rune") val pendingInboundRune: String = "0",
    @SerialName("pending_inbound_asset") val pendingInboundAsset: String = "0",
)
