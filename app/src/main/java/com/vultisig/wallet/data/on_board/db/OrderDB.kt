package com.vultisig.wallet.data.on_board.db

import android.content.Context
import com.google.gson.Gson
import com.vultisig.wallet.models.ItemPosition
import com.vultisig.wallet.models.Order
import com.vultisig.wallet.models.OrderLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class OrderDB @Inject constructor(@ApplicationContext context: Context, private val gson: Gson) {

    private val ordersFolder = context.filesDir.resolve("order")

    init {
        ordersFolder.mkdirs()
    }

    private fun orderByLocation(orderLocation: OrderLocation): Pair<File, Order?> {
        val file = ordersFolder.resolve(orderLocation)
        val order: Order? = try {
            gson.fromJson(file.readText(), Order::class.java)
        } catch (e: Exception) {
            null
        }
        return Pair(file, order)
    }


    fun initIndexes(location: OrderLocation, keys: List<String>) {
        var (file, order) = orderByLocation(location)
        if (order != null)
            return
        order = Order(location,
            positions = keys.mapIndexed { index, key -> ItemPosition(key, index) })
        file.writeText(gson.toJson(order))
    }


    fun update(location: OrderLocation, newOrderKeys: List<String>) {
        val (file, order) = orderByLocation(location)
        val positions: MutableList<ItemPosition> = mutableListOf()
        newOrderKeys.forEachIndexed { index, key ->
            positions.add(ItemPosition(key, index))
        }
        file.writeText(gson.toJson(order?.copy(positions = positions)))
    }

    fun updateItemKey(oldKey: String, newKey: String) {
        ordersFolder.listFiles()?.forEach { file ->
            val order = gson.fromJson(file.readText(), Order::class.java)
            val itemPositions = order?.positions?.toMutableList()
            val oldKeyItemPosition = itemPositions?.firstOrNull { it.key == oldKey }
            itemPositions?.remove(oldKeyItemPosition)
            itemPositions?.add(ItemPosition(newKey, oldKeyItemPosition?.position ?: 0))
            file.writeText(gson.toJson(order?.copy(positions = itemPositions ?: emptyList())))
        }
    }

    fun removeOrder(key: String) {
        ordersFolder.listFiles()?.forEach { fileLocation ->
            val order = gson.fromJson(fileLocation.readText(), Order::class.java)
            val updatedPositions =
                order.positions.toMutableList().apply { removeIf { it.key == key } }
            fileLocation.writeText(gson.toJson(order.copy(positions = updatedPositions.toList())))
        }
    }

    private fun insert(location: OrderLocation, key: String): ItemPosition {
        val (file, order) = orderByLocation(location)
        val positions = order?.positions?.toMutableList()
        val maxPosition = positions?.maxByOrNull { it.position }?.position ?: 0
        val itemPosition = ItemPosition(key, maxPosition + 1)
        positions?.add(itemPosition)
        file.writeText(gson.toJson(order?.copy(positions = positions?.toList() ?: emptyList())))
        return itemPosition
    }

    fun getPosition(orderLocation: OrderLocation, key: String): Int {
        val (_, order) = orderByLocation(orderLocation)
        val itemPosition =
            order?.positions?.firstOrNull { it.key == key } ?: insert(orderLocation, key)
        return itemPosition.position
    }

}