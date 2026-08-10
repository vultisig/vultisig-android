package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SuiTransactionBlockResponse(
    val digest: String,
    val checkpoint: Long? = null,
    val effects: SuiTransactionBlockEffects? = null,
)

@Serializable
data class SuiTransactionBlockEffects(
    @SerialName("status") val status: SuiExecutionStatus? = null,
    val transactionDigest: String? = null,
)

@Serializable data class SuiExecutionStatus(val status: String, val error: String? = null)
