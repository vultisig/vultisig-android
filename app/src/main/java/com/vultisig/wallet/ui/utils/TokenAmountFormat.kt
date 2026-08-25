package com.vultisig.wallet.ui.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Locale-aware rendering for user-facing token amounts and percentages.
 *
 * `BigDecimal.toPlainString()` always writes `.` as the decimal separator and never groups, so a
 * user reading the app in Russian, German, Dutch or Portuguese was shown `1054.427822 USDC` where
 * their locale writes `1 054,427822 USDC`. Fiat already goes through
 * `AppCurrencyRepository.getCurrencyFormat()`; these helpers are the token-side equivalent, so a
 * screen mixing the two no longer renders one number in the user's convention and the next in
 * en-US.
 *
 * The plain string form stays correct for text the app parses back — amount fields, request
 * payloads, memos — because those are read by code, not by a person.
 */
internal fun BigDecimal.formatTokenAmount(
    ticker: String? = null,
    decimals: Int = scale().coerceAtLeast(0),
    locale: Locale = Locale.getDefault(),
): String {
    val amount = formatDecimal(this, decimals, locale)
    return if (ticker.isNullOrBlank()) amount else "$amount $ticker"
}

/**
 * Renders a percentage that is already expressed in percent units — `4.00` becomes `4.00%` (or
 * `4,00%`). APY fractions must be multiplied by 100 before they get here.
 */
internal fun BigDecimal.formatPercent(
    decimals: Int = scale().coerceAtLeast(0),
    locale: Locale = Locale.getDefault(),
): String = formatDecimal(this, decimals, locale) + "%"

/**
 * The receiver's own scale is the display precision by default, so callers keep exactly the digits
 * they rounded or stripped to and only the separators change.
 */
private fun formatDecimal(value: BigDecimal, decimals: Int, locale: Locale): String =
    DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale))
        .apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            // Never round a displayed balance up — it would claim more than the position holds.
            roundingMode = RoundingMode.DOWN
        }
        .format(value)
