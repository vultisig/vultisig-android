package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vultisig.wallet.data.models.TransactionHistoryData

enum class TransactionType {
    SEND,
    SWAP,
}

enum class TransactionStatus {
    BROADCASTED,
    PENDING,
    CONFIRMED,
    FAILED,
    NotFound,
}

@Entity(
    tableName = "transaction_history",
    foreignKeys =
        [
            ForeignKey(
                entity = VaultEntity::class,
                parentColumns = ["id"],
                childColumns = ["vaultId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [
            Index("vaultId"),
            Index("txHash", unique = true),
            Index("status"),
            Index("type"),
            Index("chain"),
            Index("timestamp"),
        ],
)
data class TransactionHistoryEntity(
    /** Deterministic id `"$chain:$txHash"` so the same on-chain tx produces the same row. */
    @PrimaryKey @ColumnInfo("id") val id: String,
    @ColumnInfo("vaultId") val vaultId: String,
    @ColumnInfo("type") val type: TransactionType,
    @ColumnInfo("status") val status: TransactionStatus,
    @ColumnInfo("chain") val chain: String,
    @ColumnInfo("timestamp") val timestamp: Long,
    @ColumnInfo("txHash") val txHash: String,
    @ColumnInfo("explorerUrl") val explorerUrl: String,
    /** Type-specific JSON payload, decoded by [TransactionHistoryDataConverter]. */
    @ColumnInfo("payload") val payload: TransactionHistoryData,
    @ColumnInfo("confirmedAt") val confirmedAt: Long?,
    @ColumnInfo("failureReason") val failureReason: String?,
    @ColumnInfo("lastCheckedAt") val lastCheckedAt: Long?,
    /**
     * Consecutive poll failures — drives exponential backoff in
     * [RefreshPendingTransactionsUseCase].
     */
    @ColumnInfo("retryCount", defaultValue = "0") val retryCount: Int = 0,
)
