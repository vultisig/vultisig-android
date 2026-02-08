package com.vultisig.wallet.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class SuiTransactionBlockOptions(
    val showInput: Boolean = false,
    val showRawInput: Boolean = false,
    val showEffects: Boolean = true,
    val showEvents: Boolean = false,
    val showObjectChanges: Boolean = false,
    val showBalanceChanges: Boolean = false
)

@Serializable
data class SuiTransactionBlockResponse(
    val digest: String,
    val checkpoint: Long? = null,
    val timestampMs: String? = null,
    val effects: SuiTransactionBlockEffects? = null,
    val errors: List<String>? = null
)

@Serializable
data class SuiTransactionBlockEffects(
    @SerialName("status")
    val status: SuiExecutionStatus? = null,

    val gasUsed: SuiGasData? = null,
    val transactionDigest: String? = null
)

@Serializable
data class SuiExecutionStatus(
    val status: String,
    val error: String? = null
)

@Serializable
data class SuiGasData(
    val computationCost: String? = null,
    val storageCost: String? = null,
    val storageRebate: String? = null,
    val nonRefundableStorageFee: String? = null
)