package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.models.Coin
import java.math.BigInteger

sealed interface Transaction {
    val coin: Coin
    val amount: BigInteger
    val isMax: Boolean
}

data class Transfer(
    override val coin: Coin,
    val to: String,
    override val isMax: Boolean = false,
    override val amount: BigInteger,
    val memo: String? = null,
) : Transaction

data class Swap(
    override val coin: Coin,
    val destinationCoin: Coin,
    val to: String,
    val callData: String,
    override val isMax: Boolean = false,
    override val amount: BigInteger,
): Transaction