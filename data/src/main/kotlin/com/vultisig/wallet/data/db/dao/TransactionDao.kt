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

    /**
     * Inserts a transaction row, ignoring the insert if a row with the same primary key already
     * exists. The conflict strategy was previously [OnConflictStrategy.REPLACE], which silently
     * wiped `confirmedAt` / `failureReason` when the same row was inserted twice (e.g. the user
     * retries a broadcast and gets back the same txHash). [OnConflictStrategy.IGNORE] preserves the
     * existing row and its status metadata.
     *
     * When the backfill merge logic lands (history-2-schema PR), a dedicated `upsertWithMerge`
     * entry point replaces direct `insert` calls for paths that need to combine local and chain
     * data; `insert` continues to be used only by the local-broadcast path.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionHistoryEntity)

    @Update suspend fun update(transaction: TransactionHistoryEntity)

    @Query("SELECT * FROM transaction_history WHERE txHash = :txHash LIMIT 1")
    suspend fun getByTxHash(txHash: String): TransactionHistoryEntity?

    // Overview tab - all transactions for a vault
    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId
        ORDER BY timestamp DESC
    """
    )
    fun observeAllByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    // Send tab
    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId AND type = 'SEND'
        ORDER BY timestamp DESC
    """
    )
    fun observeSendByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    // Swap tab
    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId AND type = 'SWAP'
        ORDER BY timestamp DESC
    """
    )
    fun observeSwapByVault(vaultId: String): Flow<List<TransactionHistoryEntity>>

    // Get pending transactions for refresh.
    //
    // `NotFound` is included because it is a *transient* state — the poller looked but the
    // chain hasn't indexed the tx yet (mempool propagation delay, indexer lag). It MUST be
    // re-polled on subsequent refreshes, otherwise a row that hits NotFound once becomes a
    // permanently stuck "Pending" row in the UI (the UI renders NotFound as Pending per the
    // mapping in TransactionHistoryViewModel.toUiModel). The only terminal states are
    // CONFIRMED and FAILED, enforced by the WHERE-clause guards in the update queries below.
    @Query(
        """
        SELECT * FROM transaction_history
        WHERE vaultId = :vaultId
        AND status IN ('BROADCASTED', 'PENDING', 'NotFound')
        ORDER BY timestamp DESC
    """
    )
    suspend fun getPendingTransactions(vaultId: String): List<TransactionHistoryEntity>

    // Get all pending across all vaults (for app resume). Same `NotFound` inclusion as above.
    @Query(
        """
        SELECT * FROM transaction_history
        WHERE status IN ('BROADCASTED', 'PENDING', 'NotFound')
        ORDER BY timestamp DESC
    """
    )
    suspend fun getAllPendingTransactions(): List<TransactionHistoryEntity>

    // Terminal-state guards:
    // CONFIRMED and FAILED are treated as terminal — once a row reaches one of these states
    // it must never be downgraded back to PENDING / BROADCASTED / NotFound by a stale poll or
    // a concurrent writer. Every mutating query below includes an explicit guard so the rules
    // are enforced by the database, not the caller.

    // Update status (non-terminal transitions only).
    @Query(
        """
        UPDATE transaction_history
        SET status = :status, lastCheckedAt = :lastCheckedAt
        WHERE txHash = :txHash AND status NOT IN ('CONFIRMED', 'FAILED')
    """
    )
    suspend fun updateStatus(txHash: String, status: TransactionStatus, lastCheckedAt: Long)

    // Update to confirmed. Skips rows that are already in a terminal state to make the call
    // idempotent and safe under concurrent polling.
    @Query(
        """
        UPDATE transaction_history
        SET status = 'CONFIRMED', confirmedAt = :confirmedAt, lastCheckedAt = :lastCheckedAt
        WHERE txHash = :txHash AND status NOT IN ('CONFIRMED', 'FAILED')
    """
    )
    suspend fun updateToConfirmed(txHash: String, confirmedAt: Long, lastCheckedAt: Long)

    // Update to failed. Same terminal-state guard: a FAILED tx stays FAILED, and a CONFIRMED
    // tx is never silently flipped to FAILED from a transient poll error. Reorg-driven
    // demotions (CONFIRMED → FAILED) are intentionally out of scope here and will be handled
    // explicitly by the backfill merge logic in the history-2-schema PR.
    @Query(
        """
        UPDATE transaction_history
        SET status = 'FAILED', failureReason = :failureReason, lastCheckedAt = :lastCheckedAt
        WHERE txHash = :txHash AND status NOT IN ('CONFIRMED', 'FAILED')
    """
    )
    suspend fun updateToFailed(txHash: String, failureReason: String, lastCheckedAt: Long)
}
