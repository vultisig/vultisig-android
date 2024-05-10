package com.vultisig.wallet.models

import com.vultisig.wallet.R
import wallet.core.jni.CoinType

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

val Chain.coinType: CoinType
    get() = when (this) {
        Chain.bitcoin -> CoinType.BITCOIN
        Chain.bitcoinCash -> CoinType.BITCOINCASH
        Chain.litecoin -> CoinType.LITECOIN
        Chain.dogecoin -> CoinType.DOGECOIN
        Chain.dash -> CoinType.DASH
        Chain.thorChain -> CoinType.THORCHAIN
        Chain.mayaChain -> CoinType.THORCHAIN
        Chain.ethereum -> CoinType.ETHEREUM
        Chain.solana -> CoinType.SOLANA
        Chain.avalanche -> CoinType.AVALANCHECCHAIN
        Chain.base -> CoinType.BASE
        Chain.blast -> CoinType.BLAST
        Chain.arbitrum -> CoinType.ARBITRUM
        Chain.polygon -> CoinType.POLYGON
        Chain.optimism -> CoinType.OPTIMISM
        Chain.bscChain -> CoinType.SMARTCHAIN
        Chain.gaiaChain -> CoinType.COSMOS
        Chain.kujira -> CoinType.KUJIRA
        Chain.cronosChain -> CoinType.CRONOSCHAIN
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
    get() = when (this) {
        Chain.thorChain -> R.drawable.rune
        Chain.solana -> R.drawable.solana
        Chain.ethereum -> R.drawable.ethereum
        Chain.avalanche -> R.drawable.avax
        Chain.base -> R.drawable.eth_base
        Chain.blast -> R.drawable.eth_blast
        Chain.arbitrum -> R.drawable.eth_arbitrum
        Chain.polygon -> R.drawable.eth_polygon
        Chain.optimism -> R.drawable.eth_optimism
        Chain.bscChain -> R.drawable.bsc
        Chain.bitcoin -> R.drawable.bitcoin
        Chain.bitcoinCash -> R.drawable.bitcoincash
        Chain.litecoin -> R.drawable.litecoin
        Chain.dogecoin -> R.drawable.doge
        Chain.dash -> R.drawable.dash
        Chain.gaiaChain -> R.drawable.atom
        Chain.kujira -> R.drawable.kuji
        Chain.mayaChain -> R.drawable.cacao
        Chain.cronosChain -> R.drawable.eth_cro

    }
val Chain.IsSwapSupported: Boolean
    get() = when (this) {
        Chain.thorChain, Chain.ethereum, Chain.avalanche, Chain.bscChain, Chain.bitcoin, Chain.bitcoinCash, Chain.gaiaChain, Chain.litecoin, Chain.dogecoin -> true
        Chain.solana, Chain.dash, Chain.kujira, Chain.mayaChain, Chain.cronosChain, Chain.base, Chain.arbitrum, Chain.polygon, Chain.optimism, Chain.blast -> false
    }
