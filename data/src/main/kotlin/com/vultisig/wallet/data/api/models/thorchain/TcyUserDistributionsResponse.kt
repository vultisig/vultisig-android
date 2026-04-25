package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

@Serializable
data class TcyUserDistributionsResponse(
    val distributions: List<TcyUserDistribution>,
    val total: String? = null,
) {
    @Serializable data class TcyUserDistribution(val date: String, val amount: String)
}
