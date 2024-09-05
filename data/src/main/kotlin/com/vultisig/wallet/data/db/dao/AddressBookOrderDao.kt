package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.vultisig.wallet.data.db.BaseOrderDao
import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AddressBookOrderDao : BaseOrderDao<AddressBookOrderEntity>("addressBookOrder") {
    override fun loadOrders(parentId: String?): Flow<List<AddressBookOrderEntity>> = loadOrders()

    @Query("SELECT * FROM addressBookOrder ORDER BY `order` DESC")
    abstract fun loadOrders(): Flow<List<AddressBookOrderEntity>>
}