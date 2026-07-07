package com.vultisig.wallet.ui.models.send

import java.math.BigDecimal

/**
 * True when this amount carries more fractional digits than the token's [decimals] precision.
 *
 * Converting to base units with `movePointRight(decimals).toBigInteger()` rounds toward zero, so
 * any digit past [decimals] would be dropped silently. Trailing zeros are not real precision —
 * `1.5000000` fits a 6-decimal token — so they are stripped before the scale is compared.
 */
internal fun BigDecimal.hasExcessDecimals(decimals: Int): Boolean =
    stripTrailingZeros().scale() > decimals

/**
 * True when this string is a plain decimal number — digits and at most one decimal point.
 *
 * [String.toBigDecimalOrNull] also accepts scientific notation (`1e5`, `1E+2`), which the numeric
 * amount keyboard cannot type but which reach the field unfiltered through non-IME paths — most
 * notably a `vultisig://send?...&amount=1e5` deeplink, whose value is placed into the field
 * programmatically and therefore skips the input filter on `TokenAmountInput`. Such a value would
 * otherwise be parsed into a wrong signed amount. Hex (`0x1F`) already fails `toBigDecimalOrNull`,
 * but is rejected here too for a single, explicit plain-decimal contract that mirrors the SDK.
 */
internal fun String.isPlainDecimal(): Boolean =
    isNotEmpty() &&
        count { it == '.' } <= 1 &&
        any { it.isDigit() } &&
        all { it.isDigit() || it == '.' }

/**
 * [String.toBigDecimalOrNull] restricted to plain decimals — rejects hex and scientific notation.
 */
internal fun String.toPlainBigDecimalOrNull(): BigDecimal? =
    if (isPlainDecimal()) toBigDecimalOrNull() else null
