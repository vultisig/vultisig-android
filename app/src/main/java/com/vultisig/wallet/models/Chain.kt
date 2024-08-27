package com.vultisig.wallet.models

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenStandard.COSMOS
import com.vultisig.wallet.data.models.TokenStandard.EVM
import com.vultisig.wallet.data.models.TokenStandard.SOL
import com.vultisig.wallet.data.models.TokenStandard.UTXO
import com.vultisig.wallet.tss.TssKeyType
import wallet.core.jni.CoinType

internal enum class Chain(
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

internal val Chain.coinType: CoinType
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
internal val Chain.TssKeysignType: TssKeyType
    get() = when (this) {
        Chain.Solana, Chain.Polkadot -> TssKeyType.EDDSA
        else -> TssKeyType.ECDSA
    }


internal val Chain.logo: Int
    get() = when (this) {
        Chain.ThorChain -> R.drawable.rune
        Chain.Solana -> R.drawable.solana
        Chain.Ethereum -> R.drawable.ethereum
        Chain.Avalanche -> R.drawable.avax
        Chain.Base -> R.drawable.base
        Chain.Blast -> R.drawable.blast
        Chain.Arbitrum -> R.drawable.arbitrum
        Chain.Polygon -> R.drawable.polygon
        Chain.Optimism -> R.drawable.optimism
        Chain.BscChain -> R.drawable.bsc
        Chain.Bitcoin -> R.drawable.bitcoin
        Chain.BitcoinCash -> R.drawable.bitcoincash
        Chain.Litecoin -> R.drawable.litecoin
        Chain.Dogecoin -> R.drawable.doge
        Chain.Dash -> R.drawable.dash
        Chain.GaiaChain -> R.drawable.atom
        Chain.Kujira -> R.drawable.kuji
        Chain.MayaChain -> R.drawable.cacao
        Chain.CronosChain -> R.drawable.cro
        Chain.Polkadot -> R.drawable.dot
        Chain.Dydx -> R.drawable.dydx
        Chain.ZkSync -> R.drawable.zksync
    }

internal val Chain.canSelectTokens: Boolean
    get() = when {
        this == Chain.CronosChain || this == Chain.ZkSync -> false
        standard == EVM -> true
        this == Chain.MayaChain -> true
        else -> false
    }

internal val Chain.IsSwapSupported: Boolean
    get() = this in arrayOf(
        Chain.ThorChain, Chain.MayaChain, Chain.GaiaChain, Chain.Kujira,

        Chain.Bitcoin, Chain.Dogecoin, Chain.BitcoinCash, Chain.Litecoin, Chain.Dash,

        Chain.Avalanche, Chain.Base, Chain.BscChain, Chain.Ethereum, Chain.Optimism, Chain.Polygon,

        Chain.Arbitrum, Chain.Blast,
    )

internal val Chain.isDepositSupported: Boolean
    get() = when (this) {
        Chain.ThorChain, Chain.MayaChain -> true
        else -> false
    }

internal val Chain.isLayer2: Boolean
    get() = when (this) {
        Chain.Arbitrum, Chain.Avalanche, Chain.CronosChain, Chain.Base, Chain.Blast,
        Chain.Optimism, Chain.Polygon, Chain.BscChain, Chain.ZkSync -> true
        else -> false
    }

internal fun Chain.oneInchChainId(): Int =
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