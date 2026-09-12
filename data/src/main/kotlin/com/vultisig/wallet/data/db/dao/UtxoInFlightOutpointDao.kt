package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vultisig.wallet.data.db.models.UtxoInFlightOutpointEntity

@Dao
interface UtxoInFlightOutpointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<UtxoInFlightOutpointEntity>)

    /** Rows for one sending address broadcast at or after [since], oldest transaction first. */
    @Query(
        """
        SELECT * FROM utxo_inflight_outpoint
        WHERE chain = :chain AND address = :address AND broadcast_at >= :since
        ORDER BY broadcast_at ASC
        """
    )
    suspend fun getSince(
        chain: String,
        address: String,
        since: Long,
    ): List<UtxoInFlightOutpointEntity>

    @Query("DELETE FROM utxo_inflight_outpoint WHERE broadcast_at < :before")
    suspend fun deleteBroadcastBefore(before: Long)
}
