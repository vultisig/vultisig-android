package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

/** A single TCY staking distribution event with block, amount, and optional timestamp. */
@Serializable
data class TcyDistribution(val block: String, val amount: String, val timestamp: String? = null)
