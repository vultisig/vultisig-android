package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vultisig.wallet.data.db.models.FolderOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FolderOrderDao : BaseOrderDao<FolderOrderEntity>("folderOrder") {
    override fun loadOrders(parentId: String?): Flow<List<FolderOrderEntity>> = loadOrders()

    @Query("SELECT * FROM vaultOrder ORDER BY `order` DESC")
    abstract fun loadOrders(): Flow<List<FolderOrderEntity>>
}