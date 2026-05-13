package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

/** THORChain block number from the node status response. */
@Serializable data class BlockNumber(val thorchain: Long)
