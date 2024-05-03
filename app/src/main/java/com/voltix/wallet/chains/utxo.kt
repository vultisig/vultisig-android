package com.voltix.wallet.chains

data class UtxoInfo(
    val hash: String,
    val amount: ULong,
    val index: UInt,
) {

}