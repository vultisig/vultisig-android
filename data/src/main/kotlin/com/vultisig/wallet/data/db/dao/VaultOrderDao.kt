package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vultisig.wallet.data.db.models.VaultOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class VaultOrderDao : BaseOrderDao<VaultOrderEntity>("vaultOrder") {
    override fun loadOrders(parentId: String?): Flow<List<VaultOrderEntity>> = loadOrders()

    @Query("SELECT * FROM vaultOrder ORDER BY `order` DESC")
    abstract fun loadOrders(): Flow<List<VaultOrderEntity>>

    @Query("SELECT COUNT(*) FROM vaultOrder WHERE parentId = :parentId")
    abstract suspend fun getChildrenCountFor(parentId: String): Int
}