package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.models.Chain
import wallet.core.jni.CoinType
import wallet.core.jni.CoinTypeConfiguration
import java.math.BigDecimal
import java.math.BigInteger

val CoinType.symbol
    get() = CoinTypeConfiguration.getSymbol(this)

val CoinType.decimals
    get() = CoinTypeConfiguration.getDecimals(this)

val CoinType.id
    get() = CoinTypeConfiguration.getID(this)

fun CoinType.toUnit(value: BigDecimal): BigInteger =
    value.multiply(BigDecimal.TEN.pow(decimals)).toBigInteger()

fun CoinType.toUnit(value: BigInteger): BigInteger =
    value.multiply(BigInteger.TEN.pow(decimals))

fun CoinType.toValue(value: BigDecimal): BigDecimal =
    value.divide(BigDecimal.TEN.pow(decimals))

fun CoinType.toValue(value: BigInteger): BigDecimal =
    value.toBigDecimal().divide(BigDecimal.TEN.pow(decimals))

internal val CoinType.getDustThreshold: Long
    get() = when (this) {
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
    get() = when (this) {
        CoinType.SEI -> CoinType.ETHEREUM
        else -> this
    }

fun CoinType.compatibleChainId(chain: Chain? = null): String =
    when (this) {
        CoinType.SEI -> "1329"
        CoinType.ETHEREUM -> when (chain) {
            Chain.Hyperliquid -> "999" // override when WC has no cointype
            else -> this.chainId()
        }
        else -> this.chainId()
    }

fun CoinType.compatibleDerivationPath(): String =
    when (this) {
        CoinType.SEI -> "m/44'/60'/0'/0/0"
        else -> this.derivationPath()
    }