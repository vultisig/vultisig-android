package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.Chain.Ripple

object Tokens {

    val wewe = Coin(
        chain = Chain.Base,
        ticker = "WEWE",
        logo = "wewe",
        address = "",
        decimal = 18,
        hexPublicKey = "",
        priceProviderID = "",
        contractAddress = "0x6b9bb36519538e0C073894E964E90172E1c0B41F",
        isNativeToken = false,
    )
    val polkadot = Coin(
        chain = Chain.Polkadot,
        ticker = "DOT",
        logo = "dot",
        address = "",
        decimal = 10,
        hexPublicKey = "",
        priceProviderID = "polkadot",
        contractAddress = "",
        isNativeToken = true,
    )
    val xrp = Coin(
        chain = Ripple,
        ticker = "XRP",
        logo = "xrp",
        decimal = 6,
        priceProviderID = "ripple",
        contractAddress = "",
        isNativeToken = true,
        address = "",
        hexPublicKey = "",
    )

}