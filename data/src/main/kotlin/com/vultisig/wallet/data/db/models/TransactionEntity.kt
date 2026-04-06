package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult

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

fun TransactionResult.toDbModel() =
    when (this) {
        TransactionResult.Confirmed -> TransactionStatus.CONFIRMED
        is TransactionResult.Failed -> TransactionStatus.FAILED
        TransactionResult.NotFound -> TransactionStatus.NotFound
        TransactionResult.Pending -> TransactionStatus.PENDING
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
    // Deterministic primary key derived from chain + txHash. Two devices that see the same
    // on-chain transaction produce the same row id, which is required for cross-device dedup
    // and for the backfill merge logic that will land in the history-2-schema PR.
    @PrimaryKey @ColumnInfo("id") val id: String,

    // Common fields
    @ColumnInfo("vaultId") val vaultId: String,
    @ColumnInfo("type") val type: TransactionType,
    @ColumnInfo("status") val status: TransactionStatus,
    @ColumnInfo("chain") val chain: String,
    @ColumnInfo("timestamp") val timestamp: Long,
    @ColumnInfo("txHash") val txHash: String,
    @ColumnInfo("explorerUrl") val explorerUrl: String,

    // Type-specific fields stored as JSON; decoded by TransactionHistoryDataConverter.
    // Adding a new transaction type only requires a new @Serializable subclass — no schema change.
    @ColumnInfo("payload") val payload: TransactionHistoryData,

    // Status metadata
    @ColumnInfo("confirmedAt") val confirmedAt: Long?,
    @ColumnInfo("failureReason") val failureReason: String?,
    @ColumnInfo("lastCheckedAt") val lastCheckedAt: Long?,
)
