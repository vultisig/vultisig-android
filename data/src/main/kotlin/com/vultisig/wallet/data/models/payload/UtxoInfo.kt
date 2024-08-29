package com.vultisig.wallet.data.models.payload

data class UtxoInfo(
    val hash: String,
    val amount: Long,
    val index: UInt,
)