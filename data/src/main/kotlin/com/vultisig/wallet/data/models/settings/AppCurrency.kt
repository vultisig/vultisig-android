package com.vultisig.wallet.data.models.settings

import java.util.Locale

/**
 * [formatLocale] is the one locale every fiat figure in this currency is formatted with, whatever
 * the app language, so the same balance reads the same in every language and on every platform.
 * [symbol] replaces that locale's own symbol where it would read as another currency (`$`, `¥`).
 */
enum class AppCurrency(
    val ticker: String,
    val fullName: String,
    val formatLocale: Locale,
    val symbol: String? = null,
) {
    USD(
        ticker = "USD",
        fullName = "US Dollar ($)",
        formatLocale = Locale.forLanguageTag("en-US"),
    ),
    AUD(
        ticker = "AUD",
        fullName = "Australian Dollar (A$)",
        formatLocale = Locale.forLanguageTag("en-AU"),
        symbol = "A$",
    ),
    EUR(ticker = "EUR", fullName = "Euro (€)", formatLocale = Locale.forLanguageTag("de-DE")),
    CHF(
        ticker = "CHF",
        fullName = "Swiss Franc (CHF)", // not in screenshot, valid abbreviation kept
        formatLocale = Locale.forLanguageTag("de-CH"),
    ),
    GBP(
        ticker = "GBP",
        fullName = "British Pound (£)",
        formatLocale = Locale.forLanguageTag("en-GB"),
    ),
    JPY(
        ticker = "JPY",
        fullName = "Japanese Yen (￥)",
        formatLocale = Locale.forLanguageTag("ja-JP"),
    ),
    CNY(
        ticker = "CNY",
        fullName = "Chinese Yuan (CN¥)",
        formatLocale = Locale.forLanguageTag("zh-CN"),
        symbol = "CN¥",
    ),
    CAD(
        ticker = "CAD",
        fullName = "Canadian Dollar (CA$)",
        formatLocale = Locale.forLanguageTag("en-CA"),
        symbol = "CA$",
    ),
    SGD(
        ticker = "SGD",
        fullName = "Singapore Dollar (S$)",
        formatLocale = Locale.forLanguageTag("en-SG"),
        symbol = "S$",
    ),
    SEK(
        ticker = "SEK",
        fullName = "Swedish Krona (kr)",
        formatLocale = Locale.forLanguageTag("sv-SE"),
    ),
    RUB(
        ticker = "RUB",
        fullName = "Russian Ruble (₽)",
        formatLocale = Locale.forLanguageTag("ru-RU"),
    );

    companion object {

        fun fromTicker(ticker: String): AppCurrency? = entries.find { it.ticker == ticker }
    }
}
