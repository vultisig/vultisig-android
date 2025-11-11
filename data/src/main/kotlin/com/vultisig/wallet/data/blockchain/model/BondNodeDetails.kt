package com.vultisig.wallet.data.blockchain.model

import java.math.BigInteger
import java.util.Date

data class BondedNodePosition(
    val node: BondedNode,
    val amount: BigInteger,
    val coinId: String,
    val apy: Double,
    val nextReward: Double,
    val nextChurn: Date?,
) {
    data class BondedNode(
        val address: String,
        val state: String,
    )
}