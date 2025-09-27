package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin

internal val coin
    get() = Coin(
        chain = Chain.ThorChain,
        ticker = "RUNE",
        logo = "rune",
        address = "",
        decimal = 8,
        hexPublicKey = "",
        priceProviderID = "thorchain",
        contractAddress = "",
        isNativeToken = true,
    )

internal val account
    get() = Account(
        token = coin,
        tokenValue = null,
        fiatValue = null,
        price = null,
    )

internal val address
    get() = Address(
        chain = Chain.ThorChain,
        address = "",
        accounts = emptyList()
    )
