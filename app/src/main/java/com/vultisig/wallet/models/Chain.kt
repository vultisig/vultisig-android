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
    thorChain("THORChain", TokenStandard.THORCHAIN, "Rune"),
    mayaChain("MayaChain", TokenStandard.THORCHAIN, "cacao"),

    // ERC20
    arbitrum("Arbitrum", EVM, "Gwei"),
    avalanche("Avalanche", EVM, "Gwei"),
    base("Base", EVM, "Gwei"),
    cronosChain("CronosChain", EVM, "Gwei"),
    bscChain("BSC", EVM, "Gwei"),
    blast("Blast", EVM, "Gwei"),
    ethereum("Ethereum", EVM, "Gwei"),
    optimism("Optimism", EVM, "Gwei"),
    polygon("Polygon", EVM, "Gwei"),

    // BITCOIN
    bitcoin("Bitcoin", UTXO, "BTC/vbyte"),
    bitcoinCash("Bitcoin-Cash", UTXO, "BCH/vbyte"),
    litecoin("Litecoin", UTXO, "LTC/vbyte"),
    dogecoin("Dogecoin", UTXO, "Doge/vbyte"),
    dash("Dash", UTXO, "DASH/vbyte"),

    solana("Solana", SOL, "SOL"),
    gaiaChain("Cosmos", COSMOS, "uatom"),
    kujira("Kujira", COSMOS, "ukuji"),
    dydx("Dydx", COSMOS, "adydx"),
    polkadot("Polkadot", TokenStandard.SUBSTRATE, "DOT");

    val id: String
        get() = raw

    companion object {
        fun fromRaw(raw: String): Chain =
            Chain.entries.first { it.raw == raw }
    }
}

internal val Chain.coinType: CoinType
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
        Chain.polkadot -> CoinType.POLKADOT
        Chain.dydx -> CoinType.DYDX
    }
internal val Chain.TssKeysignType: TssKeyType
    get() = when (this) {
        Chain.bitcoin, Chain.bitcoinCash, Chain.litecoin, Chain.dogecoin, Chain.dash, Chain.thorChain, Chain.mayaChain, Chain.ethereum, Chain.avalanche, Chain.base, Chain.blast, Chain.arbitrum, Chain.polygon, Chain.optimism, Chain.bscChain, Chain.gaiaChain, Chain.kujira, Chain.cronosChain, Chain.dydx -> TssKeyType.ECDSA
        Chain.solana, Chain.polkadot -> TssKeyType.EDDSA
    }
internal val Chain.Ticker: String
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
        Chain.polkadot -> "DOT"
        Chain.dydx -> "DYDX"
    }

internal val Chain.logo: Int
    get() = when (this) {
        Chain.thorChain -> R.drawable.rune
        Chain.solana -> R.drawable.solana
        Chain.ethereum -> R.drawable.ethereum
        Chain.avalanche -> R.drawable.avax
        Chain.base -> R.drawable.base
        Chain.blast -> R.drawable.blast
        Chain.arbitrum -> R.drawable.arbitrum
        Chain.polygon -> R.drawable.polygon
        Chain.optimism -> R.drawable.optimism
        Chain.bscChain -> R.drawable.bsc
        Chain.bitcoin -> R.drawable.bitcoin
        Chain.bitcoinCash -> R.drawable.bitcoincash
        Chain.litecoin -> R.drawable.litecoin
        Chain.dogecoin -> R.drawable.doge
        Chain.dash -> R.drawable.dash
        Chain.gaiaChain -> R.drawable.atom
        Chain.kujira -> R.drawable.kuji
        Chain.mayaChain -> R.drawable.cacao
        Chain.cronosChain -> R.drawable.cro
        Chain.polkadot -> R.drawable.dot
        Chain.dydx -> R.drawable.dydx
    }

internal val Chain.tokenStandard: String?
    get() = when (this) {
        Chain.ethereum -> "ERC20"
        Chain.bscChain -> "BEP20"
        else -> null
    }

internal val Chain.canSelectTokens: Boolean
    get() = when {
        standard == EVM -> true
        else -> false
    }

internal val Chain.IsSwapSupported: Boolean
    get() = this in arrayOf(
        Chain.thorChain, Chain.mayaChain, Chain.gaiaChain, Chain.kujira,

        Chain.bitcoin, Chain.dogecoin, Chain.bitcoinCash, Chain.litecoin, Chain.dash,

        Chain.avalanche, Chain.base, Chain.bscChain, Chain.ethereum, Chain.optimism, Chain.polygon,

        Chain.arbitrum, Chain.blast,
    )

internal val Chain.isDepositSupported: Boolean
    get() = when (this) {
        Chain.thorChain, Chain.mayaChain -> true
        else -> false
    }

internal val Chain.isLayer2: Boolean
    get() = when (this) {
        Chain.arbitrum, Chain.avalanche, Chain.cronosChain, Chain.base, Chain.blast, Chain.optimism, Chain.polygon, Chain.bscChain -> true
        else -> false
    }

internal fun Chain.oneInchChainId(): Int =
    when (this) {
        Chain.ethereum -> 1
        Chain.avalanche -> 43114
        Chain.base -> 8453
        Chain.blast -> 81457
        Chain.arbitrum -> 42161
        Chain.polygon -> 137
        Chain.optimism -> 10
        Chain.bscChain -> 56
        Chain.cronosChain -> 25

        // TODO add later
        // Chain.zksync -> 324
        else -> error("Chain $this is not supported by 1inch API")
    }

internal val Chain.chainType: TokenStandard
    get() = when (this) {
        Chain.ethereum, Chain.avalanche, Chain.bscChain, Chain.arbitrum, Chain.base,
        Chain.optimism, Chain.polygon, Chain.blast, Chain.cronosChain -> TokenStandard.EVM
        Chain.thorChain, Chain.mayaChain -> TokenStandard.THORCHAIN
        Chain.solana -> TokenStandard.SOL
        Chain.bitcoin, Chain.bitcoinCash, Chain.litecoin, Chain.dogecoin, Chain.dash -> TokenStandard.UTXO
        Chain.gaiaChain, Chain.kujira, Chain.dydx -> TokenStandard.COSMOS
        Chain.polkadot -> TokenStandard.POLKADOT
    }

internal val Chain.hasCustomToken: Boolean
    get() = this != Chain.cronosChain