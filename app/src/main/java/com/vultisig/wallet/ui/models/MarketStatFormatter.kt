package com.vultisig.wallet.ui.models

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/**
 * Abbreviated number formatting for the token detail sheet's market sections.
 *
 * A market cap written out in full is a fourteen-digit number nobody reads across a two-column row;
 * `$2.23T` is the figure people actually compare. Below a million the abbreviation would lose more
 * than it saves, so those values are left to the caller's normal currency/number formatting.
 */
internal object MarketStatFormatter {

    private val MILLION = BigDecimal("1000000")
    private val BILLION = BigDecimal("1000000000")
    private val TRILLION = BigDecimal("1000000000000")

    /** Values at or above this are abbreviated; below it callers format normally. */
    val ABBREVIATION_THRESHOLD: BigDecimal = MILLION

    fun isAbbreviated(value: BigDecimal): Boolean = value.abs() >= ABBREVIATION_THRESHOLD

    /**
     * `2226290000000` -> `2.23T`. Truncates rather than rounds up, so an abbreviated figure never
     * reads as larger than the value it stands for.
     */
    fun abbreviate(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val magnitude = value.abs()
        // movePointLeft rather than divide: the scale is always a power of ten, so this can't hit
        // divide's non-terminating-expansion exception and needs no MathContext.
        val (scaled, suffix) =
            when {
                magnitude >= TRILLION -> value.movePointLeft(12) to "T"
                magnitude >= BILLION -> value.movePointLeft(9) to "B"
                magnitude >= MILLION -> value.movePointLeft(6) to "M"
                // Below the threshold there is nothing to save: five digits read fine as they are.
                else -> value to ""
            }
        return decimalFormat(locale).format(scaled) + suffix
    }

    /**
     * A token quantity with its ticker, e.g. `120.68M ETH`. Null for a non-positive supply —
     * CoinGecko reports `0` for an asset whose supply it doesn't track, and a row reading `0 ETH`
     * is worse than no row.
     */
    fun supply(value: BigDecimal, ticker: String, locale: Locale = Locale.getDefault()): String? {
        if (value <= BigDecimal.ZERO) return null
        val amount =
            if (isAbbreviated(value)) abbreviate(value, locale)
            else decimalFormat(locale).format(value)
        return "$amount $ticker"
    }

    /** `-62.1637` -> `-62.16%`, `4.2` -> `+4.20%`. */
    fun percent(value: Double, locale: Locale = Locale.getDefault()): String {
        val sign = if (value >= 0) "+" else ""
        return "$sign${"%.2f".format(locale, value)}%"
    }

    private fun decimalFormat(locale: Locale): NumberFormat =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = MAX_FRACTION_DIGITS
            minimumFractionDigits = 0
            roundingMode = RoundingMode.DOWN
        }

    /** The currency's display symbol, prefixed to an abbreviated amount the way iOS does. */
    fun currencySymbol(currencyTicker: String, locale: Locale = Locale.getDefault()): String =
        runCatching { java.util.Currency.getInstance(currencyTicker).getSymbol(locale) }
            .getOrElse { DecimalFormatSymbols.getInstance(locale).currencySymbol }

    private const val MAX_FRACTION_DIGITS = 2
}
