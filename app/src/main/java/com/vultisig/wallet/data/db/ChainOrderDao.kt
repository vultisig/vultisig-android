package com.vultisig.wallet.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.vultisig.wallet.data.db.models.ChainOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ChainOrderDao {
    @Query("SELECT * FROM chainOrder ORDER BY `order` DESC")
    fun loadByOrders(): Flow<List<ChainOrderEntity>>

    @Query("SELECT * FROM chainOrder WHERE value = :value")
    suspend fun find(value: String): ChainOrderEntity

    @Query("SELECT * FROM chainOrder WHERE `order` = (SELECT max(`order`) FROM chainOrder)")
    suspend fun getMaxChainOrder(): ChainOrderEntity?

    @Insert
    suspend fun insert(order: ChainOrderEntity)

    @Update
    suspend fun updateItemOrder(order: ChainOrderEntity)

    @Query("DELETE FROM chainOrder WHERE value = :value")
    suspend fun delete(value: String)
}