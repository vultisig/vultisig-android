package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.TokenStandard.COSMOS
import com.vultisig.wallet.data.models.TokenStandard.EVM
import com.vultisig.wallet.data.models.TokenStandard.SOL
import com.vultisig.wallet.data.models.TokenStandard.UTXO
import wallet.core.jni.CoinType

enum class Chain(
    val raw: String,
    val standard: TokenStandard,
    val feeUnit: String,
) {
    ThorChain("THORChain", TokenStandard.THORCHAIN, "Rune"),
    MayaChain("MayaChain", TokenStandard.THORCHAIN, "cacao"),

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

    Solana("Solana", SOL, "SOL"),
    GaiaChain("Cosmos", COSMOS, "uatom"),
    Kujira("Kujira", COSMOS, "ukuji"),
    Dydx("Dydx", COSMOS, "adydx"),
    Polkadot("Polkadot", TokenStandard.SUBSTRATE, "DOT");

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
    }

val Chain.TssKeysignType: TssKeyType
    get() = when (this) {
        Chain.Solana, Chain.Polkadot -> TssKeyType.EDDSA
        else -> TssKeyType.ECDSA
    }

val Chain.canSelectTokens: Boolean
    get() = when {
        this == Chain.CronosChain || this == Chain.ZkSync -> false
        standard == EVM -> true
        this == Chain.MayaChain -> true
        else -> false
    }

val Chain.IsSwapSupported: Boolean
    get() = this in arrayOf(
        Chain.ThorChain, Chain.MayaChain, Chain.GaiaChain, Chain.Kujira,

        Chain.Bitcoin, Chain.Dogecoin, Chain.BitcoinCash, Chain.Litecoin, Chain.Dash,

        Chain.Avalanche, Chain.Base, Chain.BscChain, Chain.Ethereum, Chain.Optimism, Chain.Polygon,

        Chain.Arbitrum, Chain.Blast,
    )

val Chain.isDepositSupported: Boolean
    get() = when (this) {
        Chain.ThorChain, Chain.MayaChain -> true
        else -> false
    }

val Chain.isLayer2: Boolean
    get() = when (this) {
        Chain.Arbitrum, Chain.Avalanche, Chain.CronosChain, Chain.Base, Chain.Blast,
        Chain.Optimism, Chain.Polygon, Chain.BscChain, Chain.ZkSync -> true

        else -> false
    }

fun Chain.oneInchChainId(): Int =
    when (this) {
        Chain.Ethereum -> 1
        Chain.Avalanche -> 43114
        Chain.Base -> 8453
        Chain.Blast -> 81457
        Chain.Arbitrum -> 42161
        Chain.Polygon -> 137
        Chain.Optimism -> 10
        Chain.BscChain -> 56
        Chain.CronosChain -> 25
        Chain.ZkSync -> 324
        else -> error("Chain $this is not supported by 1inch API")
    }