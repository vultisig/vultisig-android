package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.models.Chain
import java.math.BigDecimal
import java.math.BigInteger
import wallet.core.jni.CoinType
import wallet.core.jni.CoinTypeConfiguration

/**
 * WalletCore-internal symbol. UNSAFE for display: several [Chain]s share one WalletCore [CoinType],
 * so this returns the wrong ticker for MayaChain (RUNE, not CACAO), Bittensor (DOT, not TAO), and
 * Qbtc (ATOM, not QBTC). For a chain's display ticker use `Chain.nativeTokenTicker`, sourced from
 * [com.vultisig.wallet.data.models.Coins] — the single source of truth.
 */
val CoinType.symbol
    get() = CoinTypeConfiguration.getSymbol(this)

/**
 * WalletCore-internal decimals. UNSAFE for amount math on shared-CoinType chains: MayaChain (8, not
 * 10), Bittensor (10, not 9), Qbtc (6, not 8). For a chain's native amount conversion use
 * `Chain.toValue`, which is sourced from [com.vultisig.wallet.data.models.Coins].
 */
val CoinType.decimals
    get() = CoinTypeConfiguration.getDecimals(this)

val CoinType.id
    get() = CoinTypeConfiguration.getID(this)

fun CoinType.toUnit(value: BigDecimal): BigInteger =
    value.multiply(BigDecimal.TEN.pow(decimals)).toBigInteger()

fun CoinType.toUnit(value: BigInteger): BigInteger = value.multiply(BigInteger.TEN.pow(decimals))

fun CoinType.toValue(value: BigDecimal): BigDecimal = value.divide(BigDecimal.TEN.pow(decimals))

fun CoinType.toValue(value: BigInteger): BigDecimal =
    value.toBigDecimal().divide(BigDecimal.TEN.pow(decimals))

internal val CoinType.getDustThreshold: Long
    get() =
        when (this) {
            CoinType.DOGECOIN -> 1_000_000L
            CoinType.BITCOIN -> 546L
            CoinType.CARDANO -> 1_400_000L
            CoinType.LITECOIN,
            CoinType.DASH,
            CoinType.ZCASH,
            CoinType.BITCOINCASH -> 1_000L
            else -> error("Unsupported CoinType: $this")
        }

val CoinType.compatibleType: CoinType
    get() =
        when (this) {
            CoinType.SEI -> CoinType.ETHEREUM
            else -> this
        }

fun CoinType.compatibleChainId(chain: Chain? = null): String =
    when (this) {
        // SEI and Hyperliquid reuse the Ethereum coin type (see Chain.coinType), so their real EVM
        // chainIds are applied here rather than via WalletCore's chainId().
        CoinType.ETHEREUM ->
            when (chain) {
                Chain.Hyperliquid -> "999"
                Chain.Sei -> "1329"
                else -> this.chainId()
            }
        else -> this.chainId()
    }

fun CoinType.compatibleDerivationPath(): String =
    when (this) {
        CoinType.SEI -> "m/44'/60'/0'/0/0"
        else -> this.derivationPath()
    }
