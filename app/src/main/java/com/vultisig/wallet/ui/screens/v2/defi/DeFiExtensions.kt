package com.vultisig.wallet.ui.screens.v2.defi

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.utils.getCoinBy

internal val supportDeFiCoins: List<Coin>
    get() = listOf(
        Coins.getCoinBy(Chain.ThorChain, "ruji")!!,
        Coins.getCoinBy(Chain.ThorChain, "rune")!!,
        Coins.getCoinBy(Chain.ThorChain, "tcy")!!,
        Coins.getCoinBy(Chain.ThorChain, "ytcy")!!,
        Coins.getCoinBy(Chain.ThorChain, "stcy")!!
    )