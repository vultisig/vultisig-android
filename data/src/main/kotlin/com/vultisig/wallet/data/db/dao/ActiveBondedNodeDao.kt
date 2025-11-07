package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vultisig.wallet.data.db.models.ActiveBondedNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveBondedNodeDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: ActiveBondedNodeEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<ActiveBondedNodeEntity>)
    
    @Update
    suspend fun update(node: ActiveBondedNodeEntity)
    
    @Query("SELECT * FROM active_bonded_nodes WHERE vault_id = :vaultId ORDER BY amount DESC")
    fun getAllByVaultId(vaultId: String): Flow<List<ActiveBondedNodeEntity>>
    
    @Query("SELECT * FROM active_bonded_nodes WHERE vault_id = :vaultId ORDER BY amount DESC")
    suspend fun getAllByVaultIdSuspend(vaultId: String): List<ActiveBondedNodeEntity>
    
    @Query("SELECT SUM(CAST(amount AS INTEGER)) FROM active_bonded_nodes WHERE vault_id = :vaultId")
    suspend fun getTotalBondedAmount(vaultId: String): Long?
}