package com.vultisig.wallet.data.utils

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
        CoinType.LITECOIN,
        CoinType.DASH,
        CoinType.ZCASH,
        CoinType.BITCOINCASH -> 1_000L
        else -> error("Unsupported CoinType: $this")
    }