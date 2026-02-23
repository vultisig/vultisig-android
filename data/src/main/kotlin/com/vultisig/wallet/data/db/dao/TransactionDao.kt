package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionHistoryEntity)

    @Update
    suspend fun update(transaction: TransactionHistoryEntity)

    @Query("SELECT * FROM transaction_history WHERE txHash = :txHash LIMIT 1")
    suspend fun getByTxHash(txHash: String): TransactionHistoryEntity?

    // Overview tab - all transactions for a vault
    @Query("""
        SELECT * FROM transaction_history 
        WHERE vaultId = :vaultId 
        ORDER BY timestamp DESC
    """)
    fun observeAllByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    // Send tab
    @Query("""
        SELECT * FROM transaction_history 
        WHERE vaultId = :vaultId AND type = 'SEND'
        ORDER BY timestamp DESC
    """)
    fun observeSendByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    // Swap tab
    @Query("""
        SELECT * FROM transaction_history 
        WHERE vaultId = :vaultId AND type = 'SWAP'
        ORDER BY timestamp DESC
    """)
    fun observeSwapByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    // Get pending transactions for refresh
    @Query("""
        SELECT * FROM transaction_history 
        WHERE vaultId = :vaultId 
        AND status IN ('BROADCASTED', 'PENDING')
        ORDER BY timestamp DESC
    """)
    suspend fun getPendingTransactions(vaultId: String): List<TransactionHistoryEntity>

    // Get all pending across all vaults (for app resume)
    @Query("""
        SELECT * FROM transaction_history 
        WHERE status IN ('BROADCASTED', 'PENDING')
        ORDER BY timestamp DESC
    """)
    suspend fun getAllPendingTransactions(): List<TransactionHistoryEntity>

    // Filter by chain
    @Query("""
        SELECT * FROM transaction_history 
        WHERE vaultId = :vaultId 
        AND (:chain IS NULL OR chain = :chain)
        AND (:type IS NULL OR type = :type)
        ORDER BY timestamp DESC
    """)
    fun observeFiltered(
        vaultId: String,
        chain: String?,
        type: TransactionType?
    ): Flow<List<TransactionHistoryEntity>>

    // Date range filter
    @Query("""
        SELECT * FROM transaction_history 
        WHERE vaultId = :vaultId 
        AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
    """)
    fun observeByDateRange(
        vaultId: String,
        startTime: Long,
        endTime: Long
    ): Flow<List<TransactionHistoryEntity>>

    // Update status only
    @Query("""
        UPDATE transaction_history 
        SET status = :status, lastCheckedAt = :lastCheckedAt 
        WHERE txHash = :txHash
    """)
    suspend fun updateStatus(
        txHash: String,
        status: TransactionStatus,
        lastCheckedAt: Long
    )

    // Update to confirmed
    @Query("""
        UPDATE transaction_history
        SET status = 'CONFIRMED', confirmedAt = :confirmedAt, lastCheckedAt = :lastCheckedAt
        WHERE txHash = :txHash
    """)
    suspend fun updateToConfirmed(
        txHash: String,
        confirmedAt: Long,
        lastCheckedAt: Long
    )

    // Update to failed
    @Query("""
        UPDATE transaction_history
        SET status = 'FAILED', failureReason = :failureReason, lastCheckedAt = :lastCheckedAt
        WHERE txHash = :txHash
    """)
    suspend fun updateToFailed(
        txHash: String,
        failureReason: String,
        lastCheckedAt: Long
    )

    @Query("DELETE FROM transaction_history WHERE vaultId = :vaultId")
    suspend fun deleteByVault(vaultId: String)
}