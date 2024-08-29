package com.vultisig.wallet.data.models

import java.math.BigDecimal
import java.math.BigInteger

data class TokenBalance(
    val tokenValue: TokenValue?,
    val fiatValue: FiatValue?,
)

data class TokenBalanceWrapped(
    val tokenBalance: TokenBalance,
    val address: String,
    val coinId: String,
)

data class TokenValue(
    val value: BigInteger,
    val unit: String,
    val decimals: Int,
) {
    val decimal: BigDecimal
        get() = BigDecimal(value)
            .divide(BigDecimal(10).pow(decimals))

    constructor(
        value: BigInteger,
        token: Coin
    ) : this(
        value = value,
        unit = token.ticker,
        decimals = token.decimal
    )

}

data class FiatValue(
    val value: BigDecimal,
    val currency: String,
)