package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel

internal val coin
    get() = Coin(
        chain = Chain.thorChain,
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
    )

internal val address
    get() = Address(
        chain = Chain.thorChain,
        address = "",
        accounts = emptyList()
    )

internal val sendSrc
    get() = SendSrc(address, account)

internal val tokenBalanceUiModels
    get() = listOf(
        TokenBalanceUiModel(
            title = "Rune",
            balance = "0.123123",
            logo = R.drawable.rune,
            model = sendSrc,
        ),
        TokenBalanceUiModel(
            title = "Ethereum",
            balance = "0.123123",
            logo = R.drawable.ethereum,
            model = sendSrc,
        ),
    )