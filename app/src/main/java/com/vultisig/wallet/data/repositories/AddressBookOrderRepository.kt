package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.AddressBookOrderDao
import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import javax.inject.Inject

internal class AddressBookOrderRepository @Inject constructor(addressBookOrderDao: AddressBookOrderDao) :
    OrderRepositoryImpl<AddressBookOrderEntity>(addressBookOrderDao) {

    override fun defaultOrder(parentId: String?): AddressBookOrderEntity
        = AddressBookOrderEntity(order = 0f)

    override fun generateNewOrder(value: String, order: Float, parentId:String?): AddressBookOrderEntity =
        AddressBookOrderEntity(value, order)

    override fun AddressBookOrderEntity.generateUpdatedOrder(order: Float): AddressBookOrderEntity =
        copy(order = order)
}