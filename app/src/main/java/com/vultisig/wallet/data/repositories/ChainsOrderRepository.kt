package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.ChainOrderDao
import com.vultisig.wallet.data.db.models.ChainOrderEntity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject


internal interface ChainsOrderRepository {
    fun loadByOrders(): Flow<List<ChainOrderEntity>>
    suspend fun updateItemOrder(lowerVal: String?, middleVal: String, upperVal: String?)
    suspend fun delete(name: String)
    suspend fun find(name: String): ChainOrderEntity?
    suspend fun insert(name: String): Float
}


internal class ChainsOrderRepositoryImpl @Inject constructor(
    private val chainOrderDao: ChainOrderDao
) : ChainsOrderRepository {
    override fun loadByOrders(): Flow<List<ChainOrderEntity>> {
        return chainOrderDao.loadByOrders()
    }

    override suspend fun updateItemOrder(lowerVal: String?, middleVal: String, upperVal: String?) {
        withContext(IO) {
            val middleChainEntity = chainOrderDao.find(middleVal)
            val lowerChainEntity = if (lowerVal != null)
                chainOrderDao.find(lowerVal)
            else ChainOrderEntity(order = 0f)
            val upperChainEntity = if (upperVal != null)
                chainOrderDao.find(upperVal)
            else {
                val maxOrderEntity = findMaxOrder()
                ChainOrderEntity(order = maxOrderEntity.order + ORDER_STEP)
            }
            chainOrderDao.updateItemOrder(
                middleChainEntity.copy(
                    order = (lowerChainEntity.order + upperChainEntity.order) / 2
                )
            )
        }
    }

    override suspend fun delete(name: String) {
        withContext(IO) {
            chainOrderDao.delete(name)
        }
    }

    override suspend fun find(name: String): ChainOrderEntity {
        return withContext(IO) {
            chainOrderDao.find(name)
        }
    }

    private suspend fun findMaxOrder(): ChainOrderEntity =
        withContext(IO) { chainOrderDao.getMaxChainOrder() ?: ChainOrderEntity(order = 0f) }


    override suspend fun insert(name: String) = withContext(IO) {
        val maxOrder = findMaxOrder()
        val newOrder = maxOrder.order + ORDER_STEP
        val order = ChainOrderEntity(name, order = newOrder)
        chainOrderDao.insert(order)
        newOrder
    }

    companion object {
        private const val ORDER_STEP = 100_000_000
    }
}