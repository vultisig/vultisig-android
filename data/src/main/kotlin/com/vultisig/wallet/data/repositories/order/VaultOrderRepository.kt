package com.vultisig.wallet.data.repositories.order

import com.vultisig.wallet.data.db.dao.VaultOrderDao
import com.vultisig.wallet.data.db.models.VaultOrderEntity
import javax.inject.Inject

class VaultOrderRepository @Inject constructor(vaultOrderDao: VaultOrderDao) :
    OrderRepositoryImpl<VaultOrderEntity>(vaultOrderDao) {

    override fun defaultOrder(parentId: String?): VaultOrderEntity
        = VaultOrderEntity(order = 0f)

    override fun generateNewOrder(value: String, order: Float, parentId:String?): VaultOrderEntity =
        VaultOrderEntity(value, order)

    override fun VaultOrderEntity.generateUpdatedOrder(order: Float): VaultOrderEntity =
        copy(order = order)
}