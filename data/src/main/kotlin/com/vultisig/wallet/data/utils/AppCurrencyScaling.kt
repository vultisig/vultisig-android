package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.models.settings.AppCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

internal val AppCurrency.fractionDigits: Int
    get() =
        runCatching { Currency.getInstance(ticker).defaultFractionDigits }
            .getOrDefault(DEFAULT_FRACTION_DIGITS)
            .coerceAtLeast(0)

internal fun BigDecimal.scaledFor(currency: AppCurrency): BigDecimal =
    setScale(currency.fractionDigits, RoundingMode.DOWN)

private const val DEFAULT_FRACTION_DIGITS = 2
