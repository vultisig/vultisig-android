package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.models.TransactionHistoryData
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TransactionHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(transaction: TransactionHistoryEntity)

    @Query("SELECT * FROM transaction_history WHERE txHash = :txHash LIMIT 1")
    abstract suspend fun getByTxHash(txHash: String): TransactionHistoryEntity?

    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId
        ORDER BY timestamp DESC
    """
    )
    abstract fun observeAllByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId AND type = 'SEND'
        ORDER BY timestamp DESC
    """
    )
    abstract fun observeSendByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId AND type = 'SWAP'
        ORDER BY timestamp DESC
    """
    )
    abstract fun observeSwapByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    /** NotFound is transient (indexer lag) and must remain pollable. */
    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId
        AND status IN ('BROADCASTED', 'PENDING', 'NotFound')
        ORDER BY timestamp DESC
    """
    )
    abstract suspend fun getPendingTransactions(vaultId: String): List<TransactionHistoryEntity>

    @Query(
        """
        SELECT * FROM transaction_history
        WHERE status IN ('BROADCASTED', 'PENDING', 'NotFound')
        ORDER BY timestamp DESC
    """
    )
    abstract suspend fun getAllPendingTransactions(): List<TransactionHistoryEntity>

    /**
     * CONFIRMED and FAILED are terminal — the WHERE guard prevents stale writers from downgrading a
     * finalised row. retryCount resets on every successful API response (including NotFound) so
     * backoff only accumulates on network errors.
     */
    @Query(
        """
        UPDATE transaction_history
        SET status = :status, lastCheckedAt = :lastCheckedAt, retryCount = 0
        WHERE txHash = :txHash AND status NOT IN ('CONFIRMED', 'FAILED')
    """
    )
    abstract suspend fun updateStatus(
        txHash: String,
        status: TransactionStatus,
        lastCheckedAt: Long,
    )

    @Query(
        """
        UPDATE transaction_history
        SET status = 'CONFIRMED', confirmedAt = :confirmedAt,
            lastCheckedAt = :lastCheckedAt, retryCount = 0
        WHERE txHash = :txHash AND status NOT IN ('CONFIRMED', 'FAILED')
    """
    )
    abstract suspend fun updateToConfirmed(txHash: String, confirmedAt: Long, lastCheckedAt: Long)

    @Query(
        """
        UPDATE transaction_history
        SET status = 'FAILED', failureReason = :failureReason,
            lastCheckedAt = :lastCheckedAt, retryCount = 0
        WHERE txHash = :txHash AND status NOT IN ('CONFIRMED', 'FAILED')
    """
    )
    abstract suspend fun updateToFailed(txHash: String, failureReason: String, lastCheckedAt: Long)

    /** Bypasses the terminal-state guard for chain reorganisations. */
    @Query(
        """
        UPDATE transaction_history
        SET status = 'FAILED', failureReason = :reason,
            confirmedAt = NULL, lastCheckedAt = :lastCheckedAt, retryCount = 0
        WHERE txHash = :txHash AND status = 'CONFIRMED'
    """
    )
    abstract suspend fun demoteForReorg(txHash: String, reason: String, lastCheckedAt: Long)

    @Query(
        """
        UPDATE transaction_history
        SET retryCount = retryCount + 1, lastCheckedAt = :lastCheckedAt
        WHERE txHash = :txHash AND status NOT IN ('CONFIRMED', 'FAILED')
    """
    )
    abstract suspend fun incrementRetryCount(txHash: String, lastCheckedAt: Long)

    /** Guards against a concurrent status writer between snapshot read and this write. */
    @Query(
        """
        UPDATE transaction_history
        SET status = :status, confirmedAt = :confirmedAt, failureReason = :failureReason,
            lastCheckedAt = :lastCheckedAt, payload = :payload, explorerUrl = :explorerUrl,
            retryCount = 0
        WHERE id = :id AND status NOT IN ('CONFIRMED', 'FAILED')
    """
    )
    abstract suspend fun updateFromBackfillGuarded(
        id: String,
        status: TransactionStatus,
        confirmedAt: Long?,
        failureReason: String?,
        lastCheckedAt: Long?,
        payload: TransactionHistoryData,
        explorerUrl: String,
    )

    @Query(
        """
        UPDATE transaction_history
        SET payload = :payload, explorerUrl = :explorerUrl
        WHERE id = :id AND status IN ('CONFIRMED', 'FAILED')
    """
    )
    abstract suspend fun updateBackfillMetadataOnly(
        id: String,
        payload: TransactionHistoryData,
        explorerUrl: String,
    )

    /** Merges backfill data with existing rows, preserving terminal status and local metadata. */
    @Transaction
    open suspend fun upsertFromBackfill(entity: TransactionHistoryEntity) {
        val existing = getByTxHash(entity.txHash)
        if (existing == null) {
            insert(entity)
            return
        }

        val mergedLastCheckedAt =
            maxOf(existing.lastCheckedAt ?: 0L, entity.lastCheckedAt ?: 0L).takeIf { it > 0L }

        when (existing.status) {
            TransactionStatus.CONFIRMED,
            TransactionStatus.FAILED ->
                updateBackfillMetadataOnly(
                    id = existing.id,
                    payload = entity.payload,
                    explorerUrl = entity.explorerUrl,
                )

            TransactionStatus.BROADCASTED,
            TransactionStatus.PENDING,
            TransactionStatus.NotFound ->
                updateFromBackfillGuarded(
                    id = existing.id,
                    status = entity.status,
                    confirmedAt = existing.confirmedAt ?: entity.confirmedAt,
                    failureReason = existing.failureReason ?: entity.failureReason,
                    lastCheckedAt = mergedLastCheckedAt,
                    payload = entity.payload,
                    explorerUrl = entity.explorerUrl,
                )
        }
    }
}
