package com.vultisig.wallet.data.blockchain.model

import com.vultisig.wallet.data.models.Coin
import java.math.BigInteger
import java.util.Date

data class BondedNodePosition(
    val id: String, // ticker-chainId-contractAddress-node
    val node: BondedNode,
    val amount: BigInteger,
    val coin: Coin,
    val apy: Double,
    val nextReward: Double,
    val nextChurn: Date?,
) {
    companion object {
        fun Coin.generateId(nodeAddress: String): String {
            return if (contractAddress.isNotEmpty()) {
                "$id-$contractAddress-$nodeAddress"
            } else {
                "$id-$nodeAddress"
            }
        }
    }
    data class BondedNode(
        val address: String,
        val state: String,
    )
}