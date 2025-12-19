package com.vultisig.wallet.ui.screens.v2.defi

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import wallet.core.jni.CoinType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        return "0.0 ${symbol ?: coinType.symbol}"
    }
    val chainAmount = coinType.toValue(this)
    val rounded = chainAmount.setScale(8, RoundingMode.DOWN)
    return "${rounded.toPlainString()} ${symbol ?: coinType.symbol}"
}

internal fun Double.formatPercentage(): String {
    return "%.2f%%".format(Locale.US, this * 100)
}

internal fun Double.formatRuneReward(): String {
    val rewardBase = BigDecimal.valueOf(this).setScale(0, RoundingMode.DOWN).toBigInteger()
    val runeAmount =
        Chain.ThorChain.coinType.toValue(rewardBase).setScale(4, RoundingMode.DOWN)
    return "${runeAmount.toPlainString()} ${Chain.ThorChain.coinType.symbol}"
}

internal fun Double.formatToString(): String {
    val value = BigDecimal.valueOf(this).setScale(6, RoundingMode.DOWN)
    return "${value.toPlainString()} ${Chain.ThorChain.coinType.symbol}"
}