package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vultisig.wallet.data.db.models.AccountOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountOrderDao {

    @Query(
        "SELECT * FROM accountOrder WHERE vaultId = :vaultId ORDER BY isPinned DESC, `order` ASC"
    )
    fun loadOrders(vaultId: String): Flow<List<AccountOrderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<AccountOrderEntity>)

    @Query("DELETE FROM accountOrder WHERE vaultId = :vaultId")
    suspend fun deleteAll(vaultId: String)
}
