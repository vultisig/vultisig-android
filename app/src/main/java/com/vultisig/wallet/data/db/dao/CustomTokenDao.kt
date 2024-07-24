package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vultisig.wallet.data.db.models.CustomTokenEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CustomTokenDao {

    @Query("SELECT * FROM customToken WHERE chain = :chainId")
    suspend fun getAll(chainId: String): List<CustomTokenEntity>

    @Query("SELECT * FROM customToken WHERE contractAddress = :contractAddress")
    suspend fun findByContractAddress(contractAddress: String): CustomTokenEntity

    @Insert
    suspend fun insert(coin: CustomTokenEntity)

    @Query("DELETE FROM customToken where id = :id")
    suspend fun remove(id: String)

}