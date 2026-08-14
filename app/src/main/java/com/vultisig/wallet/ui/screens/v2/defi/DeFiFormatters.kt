package com.vultisig.wallet.ui.screens.v2.defi

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.utils.formatPercent
import com.vultisig.wallet.ui.utils.formatTokenAmount
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import wallet.core.jni.CoinType

internal fun String.formatAddress(): String {
    return if (this.length > 13) {
        "${this.take(9)}...${this.takeLast(3)}"
    } else {
        this
    }
}

internal fun Date?.formatDate(): String {
    return this?.let {
        val formatter = SimpleDateFormat("MMM dd, yy", Locale.US)
        formatter.format(it)
    } ?: "N/A"
}

internal fun BigInteger.formatAmount(coinType: CoinType, symbol: String? = null): String {
    if (this == BigInteger.ZERO) {
        return ZERO_AMOUNT.formatTokenAmount(symbol ?: coinType.symbol)
    }
    val chainAmount = coinType.toValue(this)
    val rounded = chainAmount.setScale(8, RoundingMode.DOWN)
    return rounded.formatTokenAmount(symbol ?: coinType.symbol)
}

internal fun BigInteger.formatAmount(decimals: Int, symbol: String): String {
    if (this == BigInteger.ZERO) {
        return ZERO_AMOUNT.formatTokenAmount(symbol)
    }
    val chainAmount = this.toBigDecimal().divide(java.math.BigDecimal.TEN.pow(decimals))
    val rounded = chainAmount.setScale(8, RoundingMode.DOWN)
    return rounded.formatTokenAmount(symbol)
}

internal fun Double.formatPercentage(): String =
    // BigDecimal cannot hold NaN or an infinity, which an APY read straight off the wire can be.
    if (!isFinite()) "$this%"
    else
        BigDecimal.valueOf(this)
            .multiply(ONE_HUNDRED)
            .setScale(PERCENTAGE_DECIMALS, RoundingMode.HALF_UP)
            .formatPercent()

internal fun Double.formatRuneReward(): String {
    val rewardBase = BigDecimal.valueOf(this).setScale(0, RoundingMode.DOWN).toBigInteger()
    val runeAmount = Chain.ThorChain.coinType.toValue(rewardBase).setScale(4, RoundingMode.DOWN)
    return runeAmount.formatTokenAmount(Chain.ThorChain.coinType.symbol)
}

internal fun Double.formatToString(): String {
    val value = BigDecimal.valueOf(this).setScale(6, RoundingMode.DOWN)
    return value.formatTokenAmount(Chain.ThorChain.coinType.symbol)
}

private const val PERCENTAGE_DECIMALS = 2
private val ONE_HUNDRED = BigDecimal(100)

/** Scale 1 so an empty position keeps reading "0.0", separator aside, as it always has. */
private val ZERO_AMOUNT = BigDecimal.ZERO.setScale(1)
