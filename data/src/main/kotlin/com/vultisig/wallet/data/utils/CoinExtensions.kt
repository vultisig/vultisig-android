package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins

fun Coins.getCoinBy(chain: Chain, ticker: String): Coin? {
    return coins[chain]?.first { it.ticker.equals(ticker, ignoreCase = true) }
}


fun String.getChain(): Chain {
    return when (this) {
        "RUNE" -> Chain.ThorChain
        "SOL" -> Chain.Solana
        "ETH" -> Chain.Ethereum
        "AVAX" -> Chain.Avalanche
        "BASE" -> Chain.Base
        "BLAST" -> Chain.Blast
        "ARB" -> Chain.Arbitrum
        "POL" -> Chain.Polygon
        "OP" -> Chain.Optimism
        "BNB" -> Chain.BscChain
        "BTC" -> Chain.Bitcoin
        "BCH" -> Chain.BitcoinCash
        "LTC" -> Chain.Litecoin
        "DOGE" -> Chain.Dogecoin
        "DASH" -> Chain.Dash
        "UATOM" -> Chain.GaiaChain
        "KUJI" -> Chain.Kujira
        "CACAO" -> Chain.MayaChain
        "CRO" -> Chain.CronosChain
        "DOT" -> Chain.Polkadot
        "DYDX" -> Chain.Dydx
        "ZK" -> Chain.ZkSync
        "SUI" -> Chain.Sui
        "TON" -> Chain.Ton
        "OSMO" -> Chain.Osmosis
        "LUNA" -> Chain.Terra
        "LUNC" -> Chain.TerraClassic
        "USDC" -> Chain.Noble
        "XRP" -> Chain.Ripple
        "AKT" -> Chain.Akash
        "TRX" -> Chain.Tron
        "ZEC" -> Chain.Zcash
        "ADA" -> Chain.Cardano
        "MNT" -> Chain.Mantle
        "SEI" -> Chain.Sei
        "HYPE" -> Chain.Hyperliquid
        else -> Chain.ThorChain
    }
}