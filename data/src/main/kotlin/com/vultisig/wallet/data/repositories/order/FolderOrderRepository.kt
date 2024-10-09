package com.vultisig.wallet.data.repositories.order

import com.vultisig.wallet.data.db.dao.FolderOrderDao
import com.vultisig.wallet.data.db.models.FolderOrderEntity
import javax.inject.Inject

class FolderOrderRepository @Inject constructor(folderOrderDao: FolderOrderDao) :
    OrderRepositoryImpl<FolderOrderEntity>(folderOrderDao) {

    override fun defaultOrder(parentId: String?): FolderOrderEntity
            = FolderOrderEntity(order = 0f)

    override fun generateNewOrder(value: String, order: Float, parentId:String?): FolderOrderEntity =
        FolderOrderEntity(value, order)

    override fun FolderOrderEntity.generateUpdatedOrder(order: Float): FolderOrderEntity =
        copy(order = order)
}