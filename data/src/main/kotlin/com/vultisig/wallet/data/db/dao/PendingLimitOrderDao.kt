package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity

@Dao
interface PendingLimitOrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: PendingLimitOrderEntity)

    @Query("SELECT * FROM pending_limit_order WHERE vault_id = :vaultId ORDER BY created_at DESC")
    suspend fun getByVaultId(vaultId: String): List<PendingLimitOrderEntity>

    @Query("SELECT * FROM pending_limit_order WHERE inbound_tx_hash = :txHash")
    suspend fun getByTxHash(txHash: String): PendingLimitOrderEntity?
}
