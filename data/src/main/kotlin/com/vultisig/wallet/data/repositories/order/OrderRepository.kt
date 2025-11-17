package com.vultisig.wallet.data.repositories.order

import com.vultisig.wallet.data.db.dao.BaseOrderDao
import com.vultisig.wallet.data.db.models.BaseOrderEntity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


interface OrderRepository<T : BaseOrderEntity> {
    fun loadOrders(parentId: String?): Flow<List<T>>
    suspend fun updateItemOrder(
        parentId: String?,
        lowerVal: String?,
        middleVal: String,
        upperVal: String?
    )

    suspend fun delete(parentId: String?, name: String)
    suspend fun deleteAll(parentId: String?)
    suspend fun find(parentId: String?, name: String): T?
    suspend fun find(name: String): T?
    suspend fun insert(parentId: String?, name: String): Float
    suspend fun insert(order: T)
    suspend fun updateList(parentId: String?, names: List<String>)
    suspend fun removeParentId(parentId: String?)
}


abstract class OrderRepositoryImpl<T : BaseOrderEntity>(
    private val baseOrderDao: BaseOrderDao<T>
) : OrderRepository<T> {
    override fun loadOrders(parentId: String?): Flow<List<T>> = baseOrderDao.loadOrders(parentId)

    override suspend fun updateItemOrder(
        parentId: String?, lowerVal: String?,
        middleVal: String, upperVal: String?
    ) {
        withContext(IO) {
            val middleEntity = baseOrderDao.find(middleVal, parentId)

            val lowerEntity =
                lowerVal?.let { baseOrderDao.find(it, parentId) } ?: generateNewOrder(
                    order = 0f,
                    parentId = parentId
                )

            val upperEntity =
                upperVal?.let { baseOrderDao.find(it, parentId) } ?: run {
                    val maxOrderEntity = findMaxOrder(parentId)
                    generateNewOrder(order = maxOrderEntity.order + ORDER_STEP, parentId = parentId)
                }

            val updatedOrderEntity = middleEntity.generateUpdatedOrder(
                (lowerEntity.order + upperEntity.order) / 2
            )
            baseOrderDao.update(updatedOrderEntity)
        }
    }

    override suspend fun delete(parentId: String?, name: String) = withContext(IO) {
        baseOrderDao.delete(name, parentId)
    }

    override suspend fun find(parentId: String?, name: String): T = withContext(IO) {
        baseOrderDao.find(name, parentId)
    }

    override suspend fun find(name: String): T? = withContext(IO) {
        baseOrderDao.safeFind(name)
    }

    private suspend fun findMaxOrder(parentId: String?): T = withContext(IO) {
        baseOrderDao.getMaxOrder(parentId) ?: defaultOrder(parentId)
    }


    override suspend fun insert(parentId: String?, name: String) = withContext(IO) {
        val maxOrder = findMaxOrder(parentId)
        val newOrder = maxOrder.order + ORDER_STEP
        val order = generateNewOrder(value = name, order = newOrder, parentId)
        baseOrderDao.insert(order)
        newOrder
    }

    override suspend fun insert(order: T) = withContext(IO){
        baseOrderDao.insert(order)
    }

    override suspend fun deleteAll(parentId: String?)  = withContext(IO){
        baseOrderDao.deleteAll(parentId)
    }

    override suspend fun updateList(parentId: String?, names: List<String>) {
        baseOrderDao.removeParentId(parentId, names)
    }

    override suspend fun removeParentId(parentId: String?) {
        baseOrderDao.removeParentId(parentId)
    }

    protected abstract fun defaultOrder(parentId: String?): T

    protected abstract fun generateNewOrder(value: String = "", order: Float, parentId: String?): T

    protected abstract fun T.generateUpdatedOrder(order: Float): T

    companion object {
        private const val ORDER_STEP = 100_000_000
    }
}