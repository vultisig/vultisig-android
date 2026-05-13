package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

/** Network-level data from the Midgard API, including bonding APY and next churn block. */
@Serializable data class MidgardNetworkData(val bondingAPY: String, val nextChurnHeight: String)
