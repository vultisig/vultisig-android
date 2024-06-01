package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.ChainOrderDao
import com.vultisig.wallet.data.db.models.ChainOrderEntity
import javax.inject.Inject

internal class ChainOrderRepository @Inject constructor(
    chainOrderDao: ChainOrderDao
) : OrderRepositoryImpl<ChainOrderEntity>(chainOrderDao) {
    override val defaultOrder: ChainOrderEntity
        get() = ChainOrderEntity(order = 0f)

    override fun generateNewOrder(value: String, order: Float): ChainOrderEntity =
        ChainOrderEntity(value, order)

    override fun ChainOrderEntity.generateUpdatedOrder(order: Float): ChainOrderEntity =
        copy(order = order)
}