package com.vultisig.wallet.models

import com.google.gson.annotations.SerializedName
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import wallet.core.jni.CoinType

internal const val WEWE_TICKER = "WEWE"

internal data class Coin(
    @SerializedName("chain")
    val chain: Chain,
    @SerializedName("ticker")
    val ticker: String,
    @SerializedName("logo")
    val logo: String,
    @SerializedName("address")
    val address: String,
    @SerializedName("decimals")
    val decimal: Int,
    @SerializedName("hexPublicKey")
    val hexPublicKey: String,
    @SerializedName("priceProviderId", alternate = ["priceProviderID"])
    val priceProviderID: String,
    @SerializedName("contractAddress")
    val contractAddress: String,
    @SerializedName("isNativeToken")
    val isNativeToken: Boolean,
) {
    val id: String
        get() = "${ticker}-${chain.id}"

    val coinType: CoinType
        get() = chain.coinType

}

internal fun Coin.AllowZeroGas(): Boolean {
    return this.chain == Chain.polkadot
}

internal object Coins {
    val SupportedCoins = listOf(
        Coin(
            chain = Chain.bitcoin,
            ticker = "BTC",
            logo = "btc",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "bitcoin",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.bitcoinCash,
            ticker = "BCH",
            logo = "bch",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "bitcoin-cash",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.litecoin,
            ticker = "LTC",
            logo = "ltc",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "litecoin",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.dogecoin,
            ticker = "DOGE",
            logo = "doge",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "dogecoin",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.dash,
            ticker = "DASH",
            logo = "dash",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "dash",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.thorChain,
            ticker = "RUNE",
            logo = "rune",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "thorchain",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.mayaChain,
            ticker = "CACAO",
            logo = "cacao",
            address = "",
            decimal = 10,
            hexPublicKey = "",
            priceProviderID = "cacao",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.mayaChain,
            ticker = "MAYA",
            logo = "maya",
            address = "",
            decimal = 4,
            hexPublicKey = "",
            priceProviderID = "maya",
            contractAddress = "",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.ethereum,
            ticker = "ETH",
            logo = "eth",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.ethereum,
            ticker = "USDC",
            logo = "usdc",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "usd-coin",
            contractAddress = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.ethereum,
            ticker = "USDT",
            logo = "usdt",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "tether",
            contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.ethereum,
            ticker = "UNI",
            logo = "uni",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "uniswap",
            contractAddress = "0x1f9840a85d5af5bf1d1762f925bdaddc4201f984",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.ethereum,
            ticker = "MATIC",
            logo = "polygon",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "matic-network",
            contractAddress = "0x7d1afa7b718fb893db30a3abc0cfc608aacfebb0",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.ethereum,
            ticker = "WBTC",
            logo = "wbtc",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "wrapped-bitcoin",
            contractAddress = "0x2260fac5e5542a773aa44fbcfedf7c193bc2c599",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.ethereum,
            ticker = "LINK",
            logo = "link",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "chainlink",
            contractAddress = "0x514910771af9ca656af840dff83e8264ecf986ca",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.ethereum,
            ticker = "FLIP",
            logo = "flip",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "chainflip",
            contractAddress = "0x826180541412d574cf1336d22c0c0a287822678a",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.ethereum,
            ticker = "TGT",
            logo = "tgt",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "thorwallet",
            contractAddress = "0x108a850856Db3f85d0269a2693D896B394C80325",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.ethereum,
            ticker = "FOX",
            logo = "fox",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "shapeshift-fox-token",
            contractAddress = "0xc770eefad204b5180df6a14ee197d99d808ee52d",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.avalanche,
            ticker = "AVAX",
            logo = "avax",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "avalanche-2",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.avalanche,
            ticker = "USDC",
            logo = "usdc",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "usd-coin",
            contractAddress = "0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.solana,
            ticker = "SOL",
            logo = "sol",
            address = "",
            decimal = 9,
            hexPublicKey = "",
            priceProviderID = "solana",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.bscChain,
            ticker = "BNB",
            logo = "bsc",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "binancecoin",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.bscChain,
            ticker = "USDT",
            logo = "usdt",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "tether",
            contractAddress = "0x55d398326f99059fF775485246999027B3197955",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.bscChain,
            ticker = "USDC",
            logo = "usdc",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "usd-coin",
            contractAddress = "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.base,
            ticker = "ETH",
            logo = "eth",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.base,
            ticker = "USDC",
            logo = "usdc",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "usd-coin",
            contractAddress = "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.base,
            ticker = WEWE_TICKER,
            logo = "wewe",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "0x6b9bb36519538e0C073894E964E90172E1c0B41F",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.arbitrum,
            ticker = "ETH",
            logo = "eth",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.arbitrum,
            ticker = "ARB",
            logo = "arbitrum",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "arbitrum",
            contractAddress = "0x912CE59144191C1204E64559FE8253a0e49E6548",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.arbitrum,
            ticker = "TGT",
            logo = "tgt",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "thorwallet",
            contractAddress = "0x429fEd88f10285E61b12BDF00848315fbDfCC341",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.arbitrum,
            ticker = "FOX",
            logo = "fox",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "shapeshift-fox-token",
            contractAddress = "0xf929de51D91C77E42f5090069E0AD7A09e513c73",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.optimism,
            ticker = "ETH",
            logo = "eth",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.optimism,
            ticker = "OP",
            logo = "optimism",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "optimism",
            contractAddress = "0x4200000000000000000000000000000000000042",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.optimism,
            ticker = "FOX",
            logo = "fox",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "shapeshift-fox-token",
            contractAddress = "0xf1a0da3367bc7aa04f8d94ba57b862ff37ced174",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.polygon,
            ticker = "ETH",
            logo = "eth",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "matic-network",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.polygon,
            ticker = "WETH",
            logo = "wETH",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "0x7ceB23fD6bC0adD59E62ac25578270cFf1b9f619",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.polygon,
            ticker = "FOX",
            logo = "fox",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "shapeshift-fox-token",
            contractAddress = "0x65a05db8322701724c197af82c9cae41195b0aa8",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.blast,
            ticker = "ETH",
            logo = "eth",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.blast,
            ticker = "WETH",
            logo = "wETH",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "0x4300000000000000000000000000000000000004",
            isNativeToken = false,
        ),
        Coin(
            chain = Chain.cronosChain,
            ticker = "CRO",
            logo = "cro",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "crypto-com-chain",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.gaiaChain,
            ticker = "ATOM",
            logo = "atom",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "cosmos",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.kujira,
            ticker = "KUJI",
            logo = "kuji",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "kujira",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.dydx,
            ticker = "DYDX",
            logo = "dydx",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "dydx-chain",
            contractAddress = "",
            isNativeToken = true,
        ),
        Coin(
            chain = Chain.polkadot,
            ticker = "DOT",
            logo = "dot",
            address = "",
            decimal = 10,
            hexPublicKey = "",
            priceProviderID = "polkadot",
            contractAddress = "",
            isNativeToken = true,
        ),
    )

    fun getCoin(ticker: String, address: String, hexPublicKey: String, coinType: CoinType): Coin? {
        return SupportedCoins.find { it.ticker == ticker && it.coinType == coinType }
            ?.copy(address = address, hexPublicKey = hexPublicKey)

    }

    fun getCoinLogo(logoName: String): ImageModel {
        return when (logoName) {
            "btc" -> R.drawable.bitcoin
            "bch" -> R.drawable.bitcoincash
            "ltc" -> R.drawable.litecoin
            "doge" -> R.drawable.doge
            "dash" -> R.drawable.dash
            "rune" -> R.drawable.rune
            "eth" -> R.drawable.ethereum
            "sol" -> R.drawable.solana
            "cacao" -> R.drawable.cacao
            "maya" -> R.drawable.maya_token_02
            "usdc" -> R.drawable.usdc
            "usdt" -> R.drawable.usdt
            "link" -> R.drawable.link
            "uni" -> R.drawable.uni
            "matic" -> R.drawable.matic
            "wbtc" -> R.drawable.wbtc
            "flip" -> R.drawable.chainflip
            "avax" -> R.drawable.avax
            "eth_optimism" -> R.drawable.eth_optimism
            "optimism" -> R.drawable.optimism
            "eth_arbitrum" -> R.drawable.eth_arbitrum
            "eth_polygon" -> R.drawable.polygon
            "eth_base" -> R.drawable.eth_base
            "bsc" -> R.drawable.bsc
            "eth_blast" -> R.drawable.eth_blast
            "blast" -> R.drawable.blast
            "eth_cro" -> R.drawable.eth_cro
            "cro" -> R.drawable.cro
            "arbitrum" -> R.drawable.arbitrum
            "kuji" -> R.drawable.kuji
            "atom" -> R.drawable.atom
            "dydx" -> R.drawable.dydx
            "polygon" -> R.drawable.polygon
            "tgt" -> R.drawable.tgt
            "fox" -> R.drawable.fox
            "dot" -> R.drawable.dot
            "wETH"->R.drawable.weth
            "wewe" -> R.drawable.wewe
            else -> logoName
        }
    }
}