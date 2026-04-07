package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionHistoryDao {

    /** Preserves existing metadata on duplicate inserts. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionHistoryEntity)

    @Update suspend fun update(transaction: TransactionHistoryEntity)

    @Query("SELECT * FROM transaction_history WHERE txHash = :txHash LIMIT 1")
    suspend fun getByTxHash(txHash: String): TransactionHistoryEntity?

    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId
        ORDER BY timestamp DESC
    """
    )
    fun observeAllByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId AND type = 'SEND'
        ORDER BY timestamp DESC
    """
    )
    fun observeSendByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId AND type = 'SWAP'
        ORDER BY timestamp DESC
    """
    )
    fun observeSwapByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    /** `NotFound` is transient and must remain pollable. */
    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId
        AND status IN ('BROADCASTED', 'PENDING', 'NotFound')
        ORDER BY timestamp DESC
    """
    )
    suspend fun getPendingTransactions(vaultId: String): List<TransactionHistoryEntity>

    @Query(
        """
        SELECT * FROM transaction_history
        WHERE status IN ('BROADCASTED', 'PENDING', 'NotFound')
        ORDER BY timestamp DESC
    """
    )
    suspend fun getAllPendingTransactions(): List<TransactionHistoryEntity>

    // CONFIRMED and FAILED are terminal. The `WHERE status NOT IN (...)` clauses below stop
    // a stale or out-of-order writer from downgrading a finalised row.

    @Query(
        """
        UPDATE transaction_history
        SET status = :status, lastCheckedAt = :lastCheckedAt
        WHERE txHash = :txHash AND status NOT IN ('CONFIRMED', 'FAILED')
    """
    )
    suspend fun updateStatus(txHash: String, status: TransactionStatus, lastCheckedAt: Long)

    @Query(
        """
        UPDATE transaction_history
        SET status = 'CONFIRMED', confirmedAt = :confirmedAt, lastCheckedAt = :lastCheckedAt
        WHERE txHash = :txHash AND status NOT IN ('CONFIRMED', 'FAILED')
    """
    )
    suspend fun updateToConfirmed(txHash: String, confirmedAt: Long, lastCheckedAt: Long)

    @Query(
        """
        UPDATE transaction_history
        SET status = 'FAILED', failureReason = :failureReason, lastCheckedAt = :lastCheckedAt
        WHERE txHash = :txHash AND status NOT IN ('CONFIRMED', 'FAILED')
    """
    )
    suspend fun updateToFailed(txHash: String, failureReason: String, lastCheckedAt: Long)
}
