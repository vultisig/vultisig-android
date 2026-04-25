package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

@Serializable
data class TcyDistribution(val block: String, val amount: String, val timestamp: String? = null)
