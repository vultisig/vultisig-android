package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.models.send.TokenBalanceUiModel

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
    )

internal val address
    get() = Address(
        chain = Chain.ThorChain,
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
            tokenLogo = R.drawable.rune,
            model = sendSrc,
            chainLogo = R.drawable.rune,
            tokenStandard = null,
            isNativeToken = true,
            isLayer2 = false,
        ),
        TokenBalanceUiModel(
            title = "Ethereum",
            balance = "0.123123",
            tokenLogo = R.drawable.ethereum,
            model = sendSrc,
            chainLogo = R.drawable.ethereum,
            tokenStandard = null,
            isNativeToken = false,
            isLayer2 = false,
        ),
    )