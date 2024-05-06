package com.vultisig.wallet.models

import android.graphics.drawable.Drawable
import com.vultisig.wallet.R

enum class Chain(val raw: String) {
    thorChain("THORChain"),
    solana("Solana"),
    ethereum("Ethereum"),
    avalanche("Avalanche"),
    base("Base"),
    blast("Blast"),
    arbitrum("Arbitrum"),
    polygon("Polygon"),
    optimism("Optimism"),
    bscChain("BSC"),
    bitcoin("Bitcoin"),
    bitcoinCash("Bitcoin Cash"),
    litecoin("Litecoin"),
    dogecoin("Dogecoin"),
    dash("Dash"),
    gaiaChain("Gaia Chain"),
    kujira("Kujira"),
    mayaChain("Maya Chain"),
    cronosChain("Cronos Chain"),
}

val Chain.Ticker: String
    get() = when (this) {
        Chain.thorChain -> "RUNE"
        Chain.solana -> "SOL"
        Chain.ethereum -> "ETH"
        Chain.avalanche -> "AVAX"
        Chain.base -> "BASE"
        Chain.blast -> "BLAST"
        Chain.arbitrum -> "ARB"
        Chain.polygon -> "MATIC"
        Chain.optimism -> "OP"
        Chain.bscChain -> "BNB"
        Chain.bitcoin -> "BTC"
        Chain.bitcoinCash -> "BCH"
        Chain.litecoin -> "LTC"
        Chain.dogecoin -> "DOGE"
        Chain.dash -> "DASH"
        Chain.gaiaChain -> "UATOM"
        Chain.kujira -> "KUJI"
        Chain.mayaChain -> "CACAO"
        Chain.cronosChain -> "CRO"
    }
val Chain.SwapAsset: String
    get() = when (this) {
        Chain.thorChain -> "thor"
        Chain.solana -> "sol"
        Chain.ethereum -> "eth"
        Chain.avalanche -> "avax"
        Chain.base -> "base"
        Chain.blast -> "blast"
        Chain.arbitrum -> "arb"
        Chain.polygon -> "matic"
        Chain.optimism -> "op"
        Chain.bscChain -> "bnb"
        Chain.bitcoin -> "btc"
        Chain.bitcoinCash -> "bch"
        Chain.litecoin -> "ltc"
        Chain.dogecoin -> "doge"
        Chain.dash -> "dash"
        Chain.gaiaChain -> "uatom"
        Chain.kujira -> "kuji"
        Chain.mayaChain -> "cacao"
        Chain.cronosChain -> "cro"
    }
val Chain.logo: Int
    get() = when(this){
        Chain.thorChain -> TODO()
        Chain.solana -> TODO()
        Chain.ethereum -> TODO()
        Chain.avalanche -> TODO()
        Chain.base -> TODO()
        Chain.blast -> TODO()
        Chain.arbitrum -> TODO()
        Chain.polygon -> TODO()
        Chain.optimism -> TODO()
        Chain.bscChain -> TODO()
        Chain.bitcoin -> R.drawable.bitcoin
        Chain.bitcoinCash -> TODO()
        Chain.litecoin -> TODO()
        Chain.dogecoin -> TODO()
        Chain.dash -> TODO()
        Chain.gaiaChain -> TODO()
        Chain.kujira -> TODO()
        Chain.mayaChain -> TODO()
        Chain.cronosChain -> TODO()

    }
val Chain.IsSwapSupported: Boolean
    get() = when (this) {
        Chain.thorChain, Chain.ethereum, Chain.avalanche, Chain.bscChain, Chain.bitcoin, Chain.bitcoinCash, Chain.gaiaChain, Chain.litecoin, Chain.dogecoin -> true
        Chain.solana, Chain.dash, Chain.kujira, Chain.mayaChain, Chain.cronosChain, Chain.base, Chain.arbitrum, Chain.polygon, Chain.optimism, Chain.blast -> false
    }
