package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.BaseOrderDao
import com.vultisig.wallet.data.db.models.BaseOrderEntity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


internal interface OrderRepository<T : BaseOrderEntity> {
    fun loadOrders(): Flow<List<T>>
    suspend fun updateItemOrder(lowerVal: String?, middleVal: String, upperVal: String?)
    suspend fun delete(name: String)
    suspend fun find(name: String): T?
    suspend fun insert(name: String): Float
}


internal abstract class OrderRepositoryImpl<T : BaseOrderEntity>(
    private val baseOrderDao: BaseOrderDao<T>
) : OrderRepository<T> {
    override fun loadOrders(): Flow<List<T>> = baseOrderDao.loadOrders()

    override suspend fun updateItemOrder(lowerVal: String?, middleVal: String, upperVal: String?) {
        withContext(IO) {
            val middleChainEntity = baseOrderDao.find(middleVal)

            val lowerChainEntity =
                lowerVal?.let { baseOrderDao.find(it) } ?: generateNewOrder(order = 0f)

            val upperChainEntity =
                upperVal?.let { baseOrderDao.find(it) } ?: run {
                    val maxOrderEntity = findMaxOrder()
                    generateNewOrder(order = maxOrderEntity.order + ORDER_STEP)
                }

            baseOrderDao.update(
                middleChainEntity.generateUpdatedOrder(
                    (lowerChainEntity.order + upperChainEntity.order) / 2
                )
            )
        }
    }

    override suspend fun delete(name: String) = withContext(IO) {
        baseOrderDao.delete(name)
    }

    override suspend fun find(name: String): T = withContext(IO) {
        baseOrderDao.find(name)
    }

    private suspend fun findMaxOrder(): T = withContext(IO) {
        baseOrderDao.getMaxOrder() ?: defaultOrder
    }


    override suspend fun insert(name: String) = withContext(IO) {
        val maxOrder = findMaxOrder()
        val newOrder = maxOrder.order + ORDER_STEP
        val order = generateNewOrder(value = name, order = newOrder)
        baseOrderDao.insert(order)
        newOrder
    }

    protected abstract val defaultOrder: T

    protected abstract fun generateNewOrder(value: String = "", order: Float): T

    protected abstract fun T.generateUpdatedOrder(order: Float): T

    companion object {
        private const val ORDER_STEP = 100_000_000
    }
}