package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.ChainOrderDao
import com.vultisig.wallet.data.db.models.ChainOrderEntity
import javax.inject.Inject

internal class ChainOrderRepository @Inject constructor(
    chainOrderDao: ChainOrderDao
) : OrderRepositoryImpl<ChainOrderEntity>(chainOrderDao) {
    override fun defaultOrder(parentId: String?): ChainOrderEntity =
        ChainOrderEntity(parentId = parentId!!,order = 0f)

    override fun generateNewOrder(value: String, order: Float,parentId:String?): ChainOrderEntity =
        ChainOrderEntity(value, order,parentId!!)

    override fun ChainOrderEntity.generateUpdatedOrder(order: Float): ChainOrderEntity =
        copy(order = order)
}