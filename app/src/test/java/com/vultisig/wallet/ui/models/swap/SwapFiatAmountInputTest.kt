@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SwapFiatAmountInputTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val tokenAmountState = TextFieldState()
    private val fiatAmountState = TextFieldState()
    private val selectedSrcToken = MutableStateFlow<Coin?>(null)
    private val currency = MutableStateFlow(AppCurrency.USD)
    private val uiState = MutableStateFlow(SwapFormUiModel())
    // One price flow per token id, so a token switch is priced by the new token.
    private val prices = mutableMapOf<String, MutableStateFlow<BigDecimal>>()
    private var conversions = 0

    private val appCurrencyRepository: AppCurrencyRepository =
        mockk(relaxed = true) {
            every { currency } returns this@SwapFiatAmountInputTest.currency
            coEvery { getCurrencyFormat(any()) } answers
                {
                    NumberFormat.getCurrencyInstance(Locale.US).apply {
                        this.currency = Currency.getInstance(firstArg<AppCurrency>().ticker)
                    }
                }
        }
    private val tokenPriceRepository: TokenPriceRepository =
        mockk(relaxed = true) {
            every { getPrice(any(), any()) } answers { priceOf(firstArg<Coin>().id) }
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typing fiat rewrites the token field at the source token's price`() =
        runTest(mainDispatcher) {
            start(backgroundScope, eth, price = "2000")

            type(fiatAmountState, "1000")

            assertEquals("0.5", tokenAmountState.text.toString())
            // Marked as a conversion so the quote pipeline keeps its typing debounce.
            assertEquals(1, conversions)
        }

    @Test
    fun `a fiat conversion does not echo back into the fiat field`() =
        runTest(mainDispatcher) {
            // 1 / 3 truncates to 0.33333333, whose mirror would be "0.99" — the typed "1" must
            // stand, or every keystroke would be rewritten under the user.
            start(backgroundScope, eth, price = "3")

            type(fiatAmountState, "1")

            assertEquals("0.33333333", tokenAmountState.text.toString())
            assertEquals("1", fiatAmountState.text.toString())
        }

    @Test
    fun `a fiat amount below one displayable token unit clears the token field`() =
        runTest(mainDispatcher) {
            start(backgroundScope, btc, price = "100000")
            type(tokenAmountState, "1")
            assertEquals("100000", fiatAmountState.text.toString())

            // 0.0001 / 100000 = 1e-9 BTC, under the 8-decimal display floor. Empty, not "0": an
            // empty field clears the quote silently where "0" reaches the pipeline as an error.
            type(fiatAmountState, "0.0001")

            assertEquals("", tokenAmountState.text.toString())
        }

    @Test
    fun `a token write mirrors fiat rounded to the currency's fraction digits`() =
        runTest(mainDispatcher) {
            start(backgroundScope, eth, price = "2000")

            // What a percentage / Max chip does: writes the token field directly.
            type(tokenAmountState, "0.1234575")

            // 246.915 rounds like the fiat line under the token amount does, so the field opens
            // on the same figure the user tapped.
            assertEquals("246.92", fiatAmountState.text.toString())
            assertEquals(0, conversions)
        }

    @Test
    fun `clearing the token field clears the fiat mirror`() =
        runTest(mainDispatcher) {
            start(backgroundScope, eth, price = "2000")
            type(tokenAmountState, "1")
            assertEquals("2000", fiatAmountState.text.toString())

            type(tokenAmountState, "")

            assertEquals("", fiatAmountState.text.toString())
        }

    @Test
    fun `switching the source token re-derives fiat from the carried-over token amount`() =
        runTest(mainDispatcher) {
            start(backgroundScope, eth, price = "2000")
            type(tokenAmountState, "1")
            assertEquals("2000", fiatAmountState.text.toString())

            // The token amount is what survives a token switch today; the fiat line follows it.
            priceOf(btc.id).value = BigDecimal("100000")
            selectedSrcToken.value = btc
            advanceUntilIdle()

            assertEquals("1", tokenAmountState.text.toString())
            assertEquals("100000", fiatAmountState.text.toString())
        }

    @Test
    fun `fiat mode is offered only while the source token has a price`() =
        runTest(mainDispatcher) {
            val input = start(backgroundScope, eth, price = "0")
            assertFalse(uiState.value.isSrcFiatInputAvailable)

            // Nothing to convert by: the tap is ignored.
            input.toggle()
            assertFalse(uiState.value.isSrcFiatInput)

            priceOf(eth.id).value = BigDecimal("2000")
            advanceUntilIdle()
            assertTrue(uiState.value.isSrcFiatInputAvailable)

            input.toggle()
            assertTrue(uiState.value.isSrcFiatInput)

            // The price going away leaves fiat mode rather than stranding a field whose
            // conversion can't run.
            priceOf(eth.id).value = BigDecimal.ZERO
            advanceUntilIdle()
            assertFalse(uiState.value.isSrcFiatInputAvailable)
            assertFalse(uiState.value.isSrcFiatInput)
        }

    @Test
    fun `toggle flips fiat mode back to token input`() =
        runTest(mainDispatcher) {
            val input = start(backgroundScope, eth, price = "2000")

            input.toggle()
            assertTrue(uiState.value.isSrcFiatInput)
            input.toggle()
            assertFalse(uiState.value.isSrcFiatInput)
        }

    @Test
    fun `the fiat symbol follows the app currency`() =
        runTest(mainDispatcher) {
            start(backgroundScope, eth, price = "2000")
            assertEquals("$", uiState.value.fiatSymbol)

            currency.value = AppCurrency.EUR
            advanceUntilIdle()

            assertEquals("€", uiState.value.fiatSymbol)
        }

    @Test
    fun `a failed price read leaves the fields blank instead of stale`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } returns
                flow { throw RuntimeException("network") }
            start(backgroundScope, eth, price = "2000")

            type(fiatAmountState, "1000")

            assertEquals("", tokenAmountState.text.toString())
            assertFalse(uiState.value.isSrcFiatInputAvailable)
        }

    private fun TestScope.start(
        scope: CoroutineScope,
        token: Coin,
        price: String,
    ): SwapFiatAmountInput {
        priceOf(token.id).value = BigDecimal(price)
        selectedSrcToken.value = token
        val input =
            SwapFiatAmountInput(
                scope = scope,
                tokenAmountState = tokenAmountState,
                fiatAmountState = fiatAmountState,
                selectedSrcToken = selectedSrcToken,
                appCurrencyRepository = appCurrencyRepository,
                tokenPriceRepository = tokenPriceRepository,
                uiState = uiState,
                onTokenAmountConverted = { conversions++ },
            )
        input.start()
        advanceUntilIdle()
        return input
    }

    private fun TestScope.type(field: TextFieldState, text: String) {
        field.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
    }

    private fun priceOf(tokenId: String): MutableStateFlow<BigDecimal> =
        prices.getOrPut(tokenId) { MutableStateFlow(BigDecimal.ZERO) }

    private val eth =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xself",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private val btc =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "",
            address = "bc1self",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "bitcoin",
            contractAddress = "",
            isNativeToken = true,
        )
}
