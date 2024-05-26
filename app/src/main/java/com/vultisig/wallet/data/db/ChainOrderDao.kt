package com.vultisig.wallet.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.vultisig.wallet.data.db.models.ChainOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ChainOrderDao {
    @Query("SELECT * FROM itemOrder order by fractionInFloat desc")
    fun loadByOrders(): Flow<List<ChainOrderEntity>>

    @Query("SELECT * FROM itemOrder where value = :value")
    suspend fun find(value: String): ChainOrderEntity

    @Query("SELECT * FROM itemOrder where fractionInFloat = (select max(fractionInFloat) from itemOrder)")
    suspend fun getMaxChainOrder(): ChainOrderEntity?

    @Insert
    suspend fun insert(order: ChainOrderEntity)

    @Update
    suspend fun updateItemOrder(order: ChainOrderEntity)

    @Query("delete from itemOrder where value = :value")
    suspend fun delete(value: String)
}