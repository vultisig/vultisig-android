package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable sealed interface TransactionHistoryData

/**
 * Runtime-only wrapper for payloads the app cannot deserialize. Never serialized by Room —
 * [TransactionHistoryDataConverter.toJson] writes [rawPayload] directly.
 */
data class UnknownTransactionHistoryData(val rawPayload: String = "") : TransactionHistoryData

data class CommonTransactionHistoryData(
    val confirmedAt: Long?,
    val failureReason: String?,
    val lastCheckedAt: Long?,
    val vaultId: String,
    val type: TransactionType,
    val status: TransactionStatus,
    val chain: String,
    val timestamp: Long,
    val txHash: String,
    val explorerUrl: String,
)

@Serializable
@SerialName("send")
data class SendTransactionHistoryData(
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val token: String,
    val tokenLogo: String,
    val feeEstimate: String,
    val memo: String,
    val fiatValue: String,
) : TransactionHistoryData

@Serializable
@SerialName("swap")
data class SwapTransactionHistoryData(
    val fromToken: String,
    val fromAmount: String,
    val fromChain: String,
    val fromTokenLogo: String,
    val toToken: String,
    val toAmount: String,
    val toChain: String,
    val toTokenLogo: String,
    val provider: String,
    val fiatValue: String,
) : TransactionHistoryData

internal fun TransactionHistoryData.toEntity(
    genericData: CommonTransactionHistoryData
): TransactionHistoryEntity =
    TransactionHistoryEntity(
        id = buildTransactionHistoryId(genericData.chain, genericData.txHash),
        vaultId = genericData.vaultId,
        type = genericData.type,
        status = genericData.status,
        chain = genericData.chain,
        timestamp = genericData.timestamp,
        txHash = genericData.txHash,
        explorerUrl = genericData.explorerUrl,
        confirmedAt = genericData.confirmedAt,
        failureReason = genericData.failureReason,
        lastCheckedAt = genericData.lastCheckedAt,
        payload = this,
    )

/** Deterministic id `"$chain:$txHash"`. The separator must not appear in either component. */
internal fun buildTransactionHistoryId(chain: String, txHash: String): String {
    require(chain.isNotEmpty()) { "chain must not be empty" }
    require(txHash.isNotEmpty()) { "txHash must not be empty" }
    require(':' !in chain) { "chain must not contain ':' — got '$chain'" }
    require(':' !in txHash) { "txHash must not contain ':' — got '$txHash'" }
    return "$chain:$txHash"
}
