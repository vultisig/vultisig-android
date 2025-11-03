package com.vultisig.wallet.ui.screens.v2.defi

import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import wallet.core.jni.CoinType
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

internal fun Date?.formatChurnDate(): String {
    return this?.let {
        val formatter = SimpleDateFormat("MMM dd, yy", Locale.US)
        formatter.format(it)
    } ?: "N/A"
}

internal fun BigInteger.formatAmount(coinType: CoinType): String {
    val chainAmount = coinType.toValue(this)
    val rounded = chainAmount.setScale(2, RoundingMode.HALF_UP)
    return "${rounded.toPlainString()} ${coinType.symbol}"
}

internal fun Double.formatApy(): String {
    return "%.2f%%".format(Locale.US, this * 100)
}