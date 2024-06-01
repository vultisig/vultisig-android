package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.VaultOrderDao
import com.vultisig.wallet.data.db.models.VaultOrderEntity
import javax.inject.Inject

internal class VaultOrderRepository @Inject constructor(vaultOrderDao: VaultOrderDao) :
    OrderRepositoryImpl<VaultOrderEntity>(vaultOrderDao) {

    override val defaultOrder: VaultOrderEntity
        get() = VaultOrderEntity(order = 0f)

    override fun generateNewOrder(value: String, order: Float): VaultOrderEntity =
        VaultOrderEntity(value, order)

    override fun VaultOrderEntity.generateUpdatedOrder(order: Float): VaultOrderEntity =
        copy(order = order)
}