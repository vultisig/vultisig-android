package com.vultisig.wallet.data.models

import java.math.BigDecimal
import java.math.BigInteger

data class TokenBalance(
    val tokenValue: TokenValue?,
    val fiatValue: FiatValue?,
)

data class TokenBalanceAndPrice(
    val tokenBalance: TokenBalance,
    val price: FiatValue?,
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
        get() = createDecimal(value, decimals)

    constructor(
        value: BigInteger,
        token: Coin
    ) : this(
        value = value,
        unit = token.ticker,
        decimals = token.decimal
    )

    companion object {
        fun createDecimal(
            value: BigInteger,
            decimals: Int,
        ) = BigDecimal(value)
            .divide(BigDecimal(10).pow(decimals))
    }

}

data class FiatValue(
    val value: BigDecimal,
    val currency: String,
) {
    operator fun plus(other: FiatValue): FiatValue {
        require(currency == other.currency) {
            "fiat currencies are not equal for $this and $other"
        }
        return FiatValue(
            value = value + other.value,
            currency = currency
        )
    }
}