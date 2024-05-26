package com.vultisig.wallet.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.vultisig.wallet.data.db.models.ChainOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ChainOrderDao {
    @Query("SELECT * FROM chain_order order by fractionInFloat desc")
    fun loadByOrders(): Flow<List<ChainOrderEntity>>

    @Query("SELECT * FROM chain_order where value = :value")
    suspend fun find(value: String): ChainOrderEntity

    @Query("SELECT * FROM chain_order where fractionInFloat = (select max(fractionInFloat) from chain_order)")
    suspend fun getMaxChainOrder(): ChainOrderEntity?

    @Insert
    suspend fun insert(order: ChainOrderEntity)

    @Update
    suspend fun updateItemOrder(order: ChainOrderEntity)

    @Query("delete from chain_order where value = :value")
    suspend fun delete(value: String)
}