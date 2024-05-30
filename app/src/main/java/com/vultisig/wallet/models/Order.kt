package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName

typealias OrderLocation = String

internal data class ItemPosition(val key: String, val position: Int)

internal data class Order(
    @SerializedName("orderLocation")
    val orderLocation: OrderLocation,
    @SerializedName("positions")
    val positions: List<ItemPosition>
)

