package com.vultisig.wallet.models

typealias OrderLocation = String

data class ItemPosition(val key: String, val position: Int)

data class Order(
    val orderLocation: OrderLocation,
    val positions: List<ItemPosition>
)

