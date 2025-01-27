package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.models.TokenStandard.COSMOS
import com.vultisig.wallet.data.models.TokenStandard.EVM
import com.vultisig.wallet.data.models.TokenStandard.RIPPLE
import com.vultisig.wallet.data.models.TokenStandard.SOL
import com.vultisig.wallet.data.models.TokenStandard.SUBSTRATE
import com.vultisig.wallet.data.models.TokenStandard.SUI
import com.vultisig.wallet.data.models.TokenStandard.THORCHAIN
import com.vultisig.wallet.data.models.TokenStandard.TON
import com.vultisig.wallet.data.models.TokenStandard.UTXO
import com.vultisig.wallet.data.models.TokenStandard.TRC20
import wallet.core.jni.CoinType

typealias ChainId = String

enum class Chain(
    val raw: ChainId,
    val standard: TokenStandard,
    val feeUnit: String,
) {
    ThorChain("THORChain", THORCHAIN, "Rune"),
    MayaChain("MayaChain", THORCHAIN, "cacao"),

    // ERC20
    Arbitrum("Arbitrum", EVM, "Gwei"),
    Avalanche("Avalanche", EVM, "Gwei"),
    Base("Base", EVM, "Gwei"),
    CronosChain("CronosChain", EVM, "Gwei"),
    BscChain("BSC", EVM, "Gwei"),
    Blast("Blast", EVM, "Gwei"),
    Ethereum("Ethereum", EVM, "Gwei"),
    Optimism("Optimism", EVM, "Gwei"),
    Polygon("Polygon", EVM, "Gwei"),
    ZkSync("Zksync", EVM, "Gwei"),

    // BITCOIN
    Bitcoin("Bitcoin", UTXO, "BTC/vbyte"),
    BitcoinCash("Bitcoin-Cash", UTXO, "BCH/vbyte"),
    Litecoin("Litecoin", UTXO, "LTC/vbyte"),
    Dogecoin("Dogecoin", UTXO, "Doge/vbyte"),
    Dash("Dash", UTXO, "DASH/vbyte"),

    GaiaChain("Cosmos", COSMOS, "uatom"),
    Kujira("Kujira", COSMOS, "ukuji"),
    Dydx("Dydx", COSMOS, "adydx"),
    Osmosis("Osmosis", COSMOS, "uosmo"),
    Terra("Terra", COSMOS, "uluna"),
    TerraClassic("TerraClassic", COSMOS, "uluna"),
    Noble("Noble", COSMOS, "uusdc"),
    Akash("Akash", COSMOS, "uakt"),

    Solana("Solana", SOL, "SOL"),
    Polkadot("Polkadot", SUBSTRATE, "DOT"),
    Sui("Sui", SUI, "SUI"),
    Ton("Ton", TON, "TON"),

    Ripple("Ripple", RIPPLE, "XRP"),
    Tron("Tron",TRC20,"TRX")
    ;

    val id: String
        get() = raw

    companion object {
        fun fromRaw(raw: String): Chain =
            Chain.entries.first { it.raw == raw }
    }
}

val Chain.coinType: CoinType
    get() = when (this) {
        Chain.Bitcoin -> CoinType.BITCOIN
        Chain.BitcoinCash -> CoinType.BITCOINCASH
        Chain.Litecoin -> CoinType.LITECOIN
        Chain.Dogecoin -> CoinType.DOGECOIN
        Chain.Dash -> CoinType.DASH
        Chain.ThorChain -> CoinType.THORCHAIN
        Chain.MayaChain -> CoinType.THORCHAIN
        Chain.Ethereum -> CoinType.ETHEREUM
        Chain.Solana -> CoinType.SOLANA
        Chain.Avalanche -> CoinType.AVALANCHECCHAIN
        Chain.Base -> CoinType.BASE
        Chain.Blast -> CoinType.BLAST
        Chain.Arbitrum -> CoinType.ARBITRUM
        Chain.Polygon -> CoinType.POLYGON
        Chain.Optimism -> CoinType.OPTIMISM
        Chain.BscChain -> CoinType.SMARTCHAIN
        Chain.GaiaChain -> CoinType.COSMOS
        Chain.Kujira -> CoinType.KUJIRA
        Chain.CronosChain -> CoinType.CRONOSCHAIN
        Chain.Polkadot -> CoinType.POLKADOT
        Chain.Dydx -> CoinType.DYDX
        Chain.ZkSync -> CoinType.ZKSYNC
        Chain.Sui -> CoinType.SUI
        Chain.Ton -> CoinType.TON
        Chain.Osmosis -> CoinType.OSMOSIS
        Chain.Terra -> CoinType.TERRAV2
        Chain.TerraClassic -> CoinType.TERRA
        Chain.Noble -> CoinType.NOBLE
        Chain.Ripple -> CoinType.XRP
        Chain.Akash -> CoinType.AKASH
        Chain.Tron -> CoinType.TRON
    }

val Chain.TssKeysignType: TssKeyType
    get() = when (this) {
        Chain.Solana, Chain.Polkadot, Chain.Sui, Chain.Ton -> TssKeyType.EDDSA
        else -> TssKeyType.ECDSA
    }

val Chain.canSelectTokens: Boolean
    get() = when (this) {
        Chain.MayaChain, Chain.Solana,
        Chain.Terra, Chain.TerraClassic,
        Chain.Sui,
        Chain.Kujira,
        Chain.Osmosis, Chain.Tron -> true

        Chain.CronosChain, Chain.ZkSync -> false
        else -> when {
            standard == EVM -> true
            else -> false
        }
    }

val Chain.IsSwapSupported: Boolean
    get() = this in arrayOf(
        Chain.ThorChain, Chain.MayaChain, Chain.GaiaChain, Chain.Kujira,

        Chain.Bitcoin, Chain.Dogecoin, Chain.BitcoinCash, Chain.Litecoin, Chain.Dash,

        Chain.Avalanche, Chain.Base, Chain.BscChain, Chain.Ethereum, Chain.Optimism, Chain.Polygon,

        Chain.Arbitrum, Chain.Blast, Chain.CronosChain, Chain.Solana, Chain.ZkSync,
    )

val Chain.isDepositSupported: Boolean
    get() = when (this) {
        Chain.ThorChain, Chain.MayaChain, Chain.Ton -> true
        else -> false
    }

val Chain.isLayer2: Boolean
    get() = when (this) {
        Chain.Arbitrum, Chain.Avalanche, Chain.CronosChain, Chain.Base, Chain.Blast,
        Chain.Optimism, Chain.Polygon, Chain.BscChain, Chain.ZkSync -> true

        else -> false
    }

fun Chain.oneInchChainId(): Long =
    when (this) {
        Chain.Ethereum -> 1
        Chain.Avalanche -> 43114
        Chain.Base -> 8453
        Chain.Solana -> 1151111081099710
        Chain.Blast -> 81457
        Chain.Arbitrum -> 42161
        Chain.Polygon -> 137
        Chain.Optimism -> 10
        Chain.BscChain -> 56
        Chain.CronosChain -> 25
        Chain.ZkSync -> 324
        else -> throw SwapException.SwapRouteNotAvailable("Chain $this is not supported by 1inch API")
    }

fun Chain.swapAssetName(): String {
    return when (this) {
        Chain.ThorChain -> "THOR"
        Chain.Ethereum -> "ETH"
        Chain.Avalanche -> "AVAX"
        Chain.BscChain -> "BSC"
        Chain.Bitcoin -> "BTC"
        Chain.BitcoinCash -> "BCH"
        Chain.Litecoin -> "LTC"
        Chain.Dogecoin -> "DOGE"
        Chain.GaiaChain -> "GAIA"
        Chain.Kujira -> "KUJI"
        Chain.Solana -> "SOL"
        Chain.Dash -> "DASH"
        Chain.MayaChain -> "MAYA"
        Chain.Arbitrum -> "ARB"
        Chain.Base -> "BASE"
        Chain.Optimism -> "OP"
        Chain.Polygon -> "POL"
        Chain.Blast -> "BLAST"
        Chain.CronosChain -> "CRO"
        Chain.Polkadot -> "DOT"
        Chain.Dydx -> "DYDX"
        Chain.ZkSync -> "ZK"
        Chain.Sui -> "SUI"
        Chain.Ton -> "TON"
        Chain.Osmosis -> "OSMO"
        Chain.Terra -> "LUNA"
        Chain.TerraClassic -> "LUNC"
        Chain.Noble -> "USDC"
        Chain.Ripple -> "XRP"
        Chain.Akash -> "AKT"
        Chain.Tron -> "TRX"
    }
}

val Chain.hasReaping: Boolean
    get() = when (this) {
        Chain.Polkadot, Chain.Ripple -> true
        else -> false
    }