package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.sources.AppDataStore
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AppCurrencyRepository {

    val defaultCurrency: AppCurrency

    val currency: Flow<AppCurrency>

    suspend fun setCurrency(currency: AppCurrency)

    fun getAllCurrencies(): List<AppCurrency>

    /** The format for whichever currency is selected right now. */
    suspend fun getCurrencyFormat(): NumberFormat

    /**
     * The format for [currency] specifically, for callers that priced their figures in a currency
     * they captured earlier. The selection can change while a load is in flight, and this stamps
     * those values with the symbol they were priced in rather than whichever one happens to be
     * selected by the time they are rendered.
     */
    suspend fun getCurrencyFormat(appCurrency: AppCurrency): NumberFormat
}

internal class AppCurrencyRepositoryImpl @Inject constructor(private val dataStore: AppDataStore) :
    AppCurrencyRepository {

    override val defaultCurrency = AppCurrency.USD

    private val mutex = Mutex()
    private var cachedLocale: Locale? = null
    private var cachedCurrency: AppCurrency? = null
    private var cachedCurrencyFormat: NumberFormat? = null

    override val currency: Flow<AppCurrency>
        get() =
            dataStore.readData(stringPreferencesKey(CURRENCY_KEY), defaultCurrency.ticker).map {
                AppCurrency.fromTicker(it) ?: defaultCurrency
            }

    override suspend fun setCurrency(currency: AppCurrency) {
        dataStore.editData { preferences ->
            preferences.set(key = stringPreferencesKey(CURRENCY_KEY), value = currency.ticker)
        }
    }

    override fun getAllCurrencies(): List<AppCurrency> {
        return CURRENCY_LIST
    }

    override suspend fun getCurrencyFormat(): NumberFormat = getCurrencyFormat(currency.first())

    override suspend fun getCurrencyFormat(appCurrency: AppCurrency): NumberFormat {
        return mutex.withLock {
            val currentLocale = resolveFormatLocale(Locale.getDefault())
            // Rebuild when either the format locale or the selected app currency changes. The
            // locale drives grouping/decimal conventions and the localized currency symbol, while
            // the selected currency drives which symbol is used, so a USD vault always renders
            // "$" even when the locale would otherwise default to another currency (e.g. en_GB
            // defaults to "£") or spell USD out as "US$".
            if (
                cachedLocale != currentLocale ||
                    cachedCurrency != appCurrency ||
                    cachedCurrencyFormat == null
            ) {
                cachedLocale = currentLocale
                cachedCurrency = appCurrency
                cachedCurrencyFormat =
                    NumberFormat.getCurrencyInstance(currentLocale).apply {
                        val selectedCurrency = Currency.getInstance(appCurrency.ticker)
                        currency = selectedCurrency
                        // Pin fraction digits to the selected currency's default. Otherwise they
                        // stay at the device-locale default currency, so a USD vault on a ja_JP /
                        // ko_KR device (JPY/KRW have 0 fraction digits) would render "$1,235" and
                        // drop the cents.
                        minimumFractionDigits = selectedCurrency.defaultFractionDigits
                        maximumFractionDigits = selectedCurrency.defaultFractionDigits
                    }
            }
            // Return a clone so concurrent callers never share a mutable NumberFormat instance.
            requireNotNull(cachedCurrencyFormat).clone() as NumberFormat
        }
    }

    // Older app versions applied "en-GB" as the application locale for the English entry, and
    // en_GB renders USD as "US$" instead of "$". Those users keep the persisted en-GB app locale
    // after updating, so format English with US conventions (identical grouping and decimal
    // separators, "$" for USD) to keep fiat amounts consistent with a fresh install.
    private fun resolveFormatLocale(locale: Locale): Locale =
        if (locale.language == ENGLISH_LANGUAGE && locale.country == UK_COUNTRY) Locale.US
        else locale

    companion object {
        private const val CURRENCY_KEY = "currency_key"
        private const val ENGLISH_LANGUAGE = "en"
        private const val UK_COUNTRY = "GB"
    }
}
