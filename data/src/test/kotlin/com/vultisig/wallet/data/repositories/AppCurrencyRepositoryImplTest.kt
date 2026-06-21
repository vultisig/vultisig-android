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
        val usdSymbol = Currency.getInstance("USD").getSymbol(Locale.UK)
        assertFalse(formatted.contains(gbpSymbol), "Should not use the device locale currency (£)")
        assertTrue(formatted.contains(usdSymbol), "Should use the selected currency (USD)")
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
