package com.vultisig.wallet.data.models
import com.vultisig.wallet.models.Coin

internal data class ChainAccount(
    val chainName: String,
    val logo: Int,
    val address: String,
    /**
     amount of native token for this chain on the address,
     null if unknown yet
     */
    val nativeTokenAmount: String?,
    /**
    amount of native token for this chain on the address in fiat,
    null if unknown yet
     */
    val fiatAmount: String?,
    val coins: MutableList<Coin> = emptyList<Coin>().toMutableList()

)