package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.AccountOrderDao
import com.vultisig.wallet.data.db.models.AccountOrderEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class AccountOrderRepository @Inject constructor(private val accountOrderDao: AccountOrderDao) {

    fun loadOrders(vaultId: String): Flow<List<AccountOrderEntity>> =
        accountOrderDao.loadOrders(vaultId)

    suspend fun saveOrders(vaultId: String, orders: List<AccountOrderEntity>) {
        accountOrderDao.deleteAll(vaultId)
        accountOrderDao.insertAll(orders)
    }
}
