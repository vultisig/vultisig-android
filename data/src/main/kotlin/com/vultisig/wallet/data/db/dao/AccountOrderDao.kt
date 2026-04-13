package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vultisig.wallet.data.db.models.AccountOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AccountOrderDao {

    @Query(
        "SELECT * FROM accountOrder WHERE vaultId = :vaultId ORDER BY isPinned DESC, `order` ASC, chain ASC"
    )
    abstract fun loadOrders(vaultId: String): Flow<List<AccountOrderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(orders: List<AccountOrderEntity>)

    @Query("DELETE FROM accountOrder WHERE vaultId = :vaultId")
    abstract suspend fun deleteAll(vaultId: String)

    @Transaction
    open suspend fun replaceAll(vaultId: String, orders: List<AccountOrderEntity>) {
        deleteAll(vaultId)
        insertAll(orders)
    }
}
