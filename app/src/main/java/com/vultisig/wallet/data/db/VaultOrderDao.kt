package com.vultisig.wallet.data.db

import androidx.room.Dao
import androidx.room.Query
import com.vultisig.wallet.data.db.models.VaultOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class VaultOrderDao : BaseOrderDao<VaultOrderEntity>("vaultOrder") {
    @Query("SELECT * FROM vaultOrder ORDER BY `order` DESC")
    abstract override fun loadOrders(): Flow<List<VaultOrderEntity>>
}