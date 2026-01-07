package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vultisig.wallet.data.db.models.StakingDetailsEntity

@Dao
interface StakingDetailsDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stakingDetails: StakingDetailsEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stakingDetailsList: List<StakingDetailsEntity>)
    
    @Update
    suspend fun update(stakingDetails: StakingDetailsEntity)
    
    @Query("SELECT * FROM staking_details WHERE vault_id = :vaultId")
    suspend fun getAllByVaultIdSuspend(vaultId: String): List<StakingDetailsEntity>
    
    @Query("SELECT * FROM staking_details WHERE vault_id = :vaultId AND coin_id = :coinId")
    suspend fun getByVaultIdAndCoinId(vaultId: String, coinId: String): StakingDetailsEntity?

    @Query("SELECT * FROM staking_details WHERE vault_id = :vaultId AND id = :id")
    suspend fun getByVaultIdAndId(vaultId: String, id: String): StakingDetailsEntity?

    @Query("DELETE FROM staking_details WHERE vault_id = :vaultId")
    suspend fun deleteAllByVaultId(vaultId: String)

    @Query("DELETE FROM staking_details WHERE vault_id = :vaultId AND coin_id = :coinId")
    suspend fun deleteByVaultIdAndCoinId(vaultId: String, coinId: String)
}