package com.vultisig.wallet.data.models

import java.math.BigDecimal
import java.math.BigInteger

internal data class TokenBalance(
    val tokenValue: TokenValue?,
    val fiatValue: FiatValue?,
)

internal data class TokenValue(
    val value: BigInteger,
    val unit: String,
    val decimals: Int,
) {
    val decimal: BigDecimal
        get() = BigDecimal(value)
            .divide(BigDecimal(10).pow(decimals))

}

internal data class FiatValue(
    val value: BigDecimal,
    val currency: String,
)