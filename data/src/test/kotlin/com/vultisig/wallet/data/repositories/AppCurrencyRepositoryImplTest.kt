package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.sources.AppDataStore
import java.util.Currency
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class AppCurrencyRepositoryImplTest {

    private val originalLocale: Locale = Locale.getDefault()

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `format uses selected currency symbol not the device locale currency`() = runTest {
        // A UK device locale would otherwise default the currency symbol to GBP (£) — the bug
        // reported in #4967 where a USD vault showed amounts in £ on the THORChain DeFi tab.
        Locale.setDefault(Locale.UK)
        val repository = AppCurrencyRepositoryImpl(FakeCurrencyDataStore(AppCurrency.USD.ticker))

        val formatted = repository.getCurrencyFormat().format(1234.5)

        val gbpSymbol = Currency.getInstance("GBP").getSymbol(Locale.UK)
        assertFalse(formatted.contains(gbpSymbol), "Should not use the device locale currency (£)")
        assertTrue(formatted.startsWith("$"), "Should use the selected currency (USD)")
    }

    @Test
    fun `format renders USD as dollar sign on an en-GB locale`() = runTest {
        // Selecting "English" used to apply the en-GB application locale, which renders USD as
        // "US$" instead of "$" (#5457). Existing users keep that persisted app locale after
        // updating, so English is formatted with US conventions.
        Locale.setDefault(Locale.UK)
        val repository = AppCurrencyRepositoryImpl(FakeCurrencyDataStore(AppCurrency.USD.ticker))

        val formatted = repository.getCurrencyFormat().format(14.32)

        assertEquals("$14.32", formatted)
    }

    @Test
    fun `format keeps the device locale conventions for other english locales`() = runTest {
        Locale.setDefault(Locale.forLanguageTag("en-AU"))
        val repository = AppCurrencyRepositoryImpl(FakeCurrencyDataStore(AppCurrency.AUD.ticker))

        val formatted = repository.getCurrencyFormat().format(1000)

        assertEquals("$1,000.00", formatted)
    }

    @Test
    fun `format renders the selected non-USD currency`() = runTest {
        Locale.setDefault(Locale.US)
        val repository = AppCurrencyRepositoryImpl(FakeCurrencyDataStore(AppCurrency.EUR.ticker))

        val formatted = repository.getCurrencyFormat().format(1000)

        assertEquals("€1,000.00", formatted)
    }

    @Test
    fun `format keeps the selected currency fraction digits on a zero-decimal device locale`() =
        runTest {
            // On ja_JP the device-locale default currency is JPY (0 fraction digits). Without
            // pinning the fraction digits to the selected currency, a USD vault would render
            // "$1,235" and drop the cents (#4982 review).
            Locale.setDefault(Locale.JAPAN)
            val repository =
                AppCurrencyRepositoryImpl(FakeCurrencyDataStore(AppCurrency.USD.ticker))

            val formatted = repository.getCurrencyFormat().format(1234.5)

            val usdSymbol = Currency.getInstance("USD").getSymbol(Locale.JAPAN)
            assertEquals("${usdSymbol}1,234.50", formatted)
        }

    @Test
    fun `format reflects a currency change between calls`() = runTest {
        Locale.setDefault(Locale.US)
        val dataStore = FakeCurrencyDataStore(AppCurrency.USD.ticker)
        val repository = AppCurrencyRepositoryImpl(dataStore)

        val usd = repository.getCurrencyFormat().format(1)
        dataStore.ticker = AppCurrency.GBP.ticker
        val gbp = repository.getCurrencyFormat().format(1)

        assertEquals("$1.00", usd)
        assertEquals("£1.00", gbp)
    }

    @Test
    fun `format for a given currency ignores the selection, which may have moved on`() = runTest {
        // A caller that priced its figures in euros asks for euros, even once the user has
        // switched: the alternative stamps the new symbol on values priced in the old currency.
        Locale.setDefault(Locale.US)
        val repository = AppCurrencyRepositoryImpl(FakeCurrencyDataStore(AppCurrency.USD.ticker))

        val formatted = repository.getCurrencyFormat(AppCurrency.EUR).format(1000)

        assertEquals("€1,000.00", formatted)
    }

    private class FakeCurrencyDataStore(var ticker: String) : AppDataStore {
        @Suppress("UNCHECKED_CAST")
        override fun <T> readData(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
            flowOf(ticker as T)

        @Suppress("UNCHECKED_CAST")
        override fun <T> readData(key: Preferences.Key<T>): Flow<T?> = flowOf(ticker as? T)

        override suspend fun editData(
            transform: suspend (MutablePreferences) -> Unit
        ): Preferences = throw UnsupportedOperationException()

        override suspend fun <T> set(key: Preferences.Key<T>, value: T) =
            throw UnsupportedOperationException()
    }
}
