package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingLimitOrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: PendingLimitOrderEntity)

    @Query("SELECT * FROM pending_limit_order WHERE vault_id = :vaultId ORDER BY created_at DESC")
    suspend fun getByVaultId(vaultId: String): List<PendingLimitOrderEntity>

    @Query("SELECT * FROM pending_limit_order WHERE vault_id = :vaultId ORDER BY created_at DESC")
    fun observeByVaultId(vaultId: String): Flow<List<PendingLimitOrderEntity>>

    @Query("SELECT * FROM pending_limit_order WHERE inbound_tx_hash = :txHash")
    suspend fun getByTxHash(txHash: String): PendingLimitOrderEntity?

    /**
     * Every order of [vaultId] that is still resting.
     *
     * `cancelling` counts as resting on purpose: a broadcast cancel is a claim about our
     * transaction, not about the order, and an order whose cancel silently matched nothing is still
     * in the queue and must keep being polled.
     */
    @Query(
        "SELECT * FROM pending_limit_order WHERE vault_id = :vaultId " +
            "AND status IN ('pending', 'cancelling') ORDER BY created_at DESC"
    )
    suspend fun getOpenByVaultId(vaultId: String): List<PendingLimitOrderEntity>

    /**
     * Record what a poll observed about a still-resting order.
     *
     * The status is deliberately NOT written here — [recordStatus] owns that, so a resting
     * observation cannot clobber the `cancelling` a local cancel record put there. Every field
     * coalesces, so a partial observation never blanks a value an earlier poll established; blank
     * observed assets are dropped by the caller rather than stored, because a blank asset is not an
     * observation and persisting one would overwrite a value a cancel memo could actually be built
     * from.
     */
    @Query(
        """
        UPDATE pending_limit_order SET
            deposit_amount = COALESCE(:depositAmount, deposit_amount),
            filled_in_amount = COALESCE(:filledInAmount, filled_in_amount),
            filled_out_amount = COALESCE(:filledOutAmount, filled_out_amount),
            observed_trade_target = COALESCE(:observedTradeTarget, observed_trade_target),
            observed_source_asset = COALESCE(:observedSourceAsset, observed_source_asset),
            observed_target_asset = COALESCE(:observedTargetAsset, observed_target_asset),
            time_to_expiry_blocks = COALESCE(:timeToExpiryBlocks, time_to_expiry_blocks),
            expiry_observed_at = CASE WHEN :timeToExpiryBlocks IS NULL
                THEN expiry_observed_at ELSE :observedAt END
        WHERE inbound_tx_hash = :txHash
        """
    )
    suspend fun recordObservation(
        txHash: String,
        depositAmount: String?,
        filledInAmount: String?,
        filledOutAmount: String?,
        observedTradeTarget: String?,
        observedSourceAsset: String?,
        observedTargetAsset: String?,
        timeToExpiryBlocks: Int?,
        observedAt: Long,
    )

    @Query("UPDATE pending_limit_order SET status = :status WHERE inbound_tx_hash = :txHash")
    suspend fun recordStatus(txHash: String, status: String)

    /**
     * Record a broadcast cancel and move the order to `cancelling` in one write, so the two can
     * never disagree.
     *
     * Only ever applied to an order that is still open: recording a cancel against an order that
     * has already closed would resurrect it in the resting list.
     */
    @Query(
        "UPDATE pending_limit_order SET cancel_broadcast_hash = :cancelTxHash, " +
            "cancel_confirmed = 0, status = 'cancelling' " +
            "WHERE inbound_tx_hash = :txHash AND status IN ('pending', 'cancelling')"
    )
    suspend fun recordCancelBroadcast(txHash: String, cancelTxHash: String)

    /**
     * Withdraw a cancel record whose transaction the chain refused, dropping the order back to
     * `pending`.
     *
     * Compare-and-set on the hash actually verified: the verification is a network round-trip, and
     * a cancel recorded in the meantime is a different transaction this verdict says nothing about.
     */
    @Query(
        "UPDATE pending_limit_order SET cancel_broadcast_hash = NULL, cancel_confirmed = 0, " +
            "status = 'pending' " +
            "WHERE inbound_tx_hash = :txHash AND cancel_broadcast_hash = :expecting " +
            "AND status = 'cancelling'"
    )
    suspend fun clearCancelBroadcast(txHash: String, expecting: String)

    @Query(
        "UPDATE pending_limit_order SET cancel_confirmed = 1 " +
            "WHERE inbound_tx_hash = :txHash AND cancel_broadcast_hash = :expecting"
    )
    suspend fun confirmCancelBroadcast(txHash: String, expecting: String)
}
