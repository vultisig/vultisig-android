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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
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
    // One price flow per token id and currency, so a token or currency switch is priced anew.
    private val prices = mutableMapOf<Pair<String, AppCurrency>, MutableStateFlow<BigDecimal>>()
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
            every { getPrice(any(), any()) } answers
                {
                    priceOf(firstArg<Coin>().id, secondArg<AppCurrency>())
                }
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

            tokenAmountState.text.toString() shouldBe "0.5"
            // Marked as a conversion so the quote pipeline keeps its typing debounce.
            conversions shouldBe 1
        }

    @Test
    fun `a fiat conversion does not echo back into the fiat field`() =
        runTest(mainDispatcher) {
            // 1 / 3 truncates to 0.33333333, whose mirror would be "0.99" — the typed "1" must
            // stand, or every keystroke would be rewritten under the user.
            start(backgroundScope, eth, price = "3")

            type(fiatAmountState, "1")

            tokenAmountState.text.toString() shouldBe "0.33333333"
            fiatAmountState.text.toString() shouldBe "1"
        }

    @Test
    fun `a fiat amount below one displayable token unit clears the token field`() =
        runTest(mainDispatcher) {
            start(backgroundScope, btc, price = "100000")
            type(tokenAmountState, "1")
            fiatAmountState.text.toString() shouldBe "100000"

            // 0.0001 / 100000 = 1e-9 BTC, under the 8-decimal display floor. Empty, not "0": an
            // empty field clears the quote silently where "0" reaches the pipeline as an error.
            type(fiatAmountState, "0.0001")

            tokenAmountState.text.toString() shouldBe ""
        }

    @Test
    fun `a token write mirrors fiat rounded to the currency's fraction digits`() =
        runTest(mainDispatcher) {
            start(backgroundScope, eth, price = "2000")

            // What a percentage / Max chip does: writes the token field directly.
            type(tokenAmountState, "0.1234575")

            // 246.915 rounds like the fiat line under the token amount does, so the field opens
            // on the same figure the user tapped.
            fiatAmountState.text.toString() shouldBe "246.92"
            conversions shouldBe 0
        }

    @Test
    fun `clearing the token field clears the fiat mirror`() =
        runTest(mainDispatcher) {
            start(backgroundScope, eth, price = "2000")
            type(tokenAmountState, "1")
            fiatAmountState.text.toString() shouldBe "2000"

            type(tokenAmountState, "")

            fiatAmountState.text.toString() shouldBe ""
        }

    @Test
    fun `switching the source token re-derives fiat from the carried-over token amount`() =
        runTest(mainDispatcher) {
            start(backgroundScope, eth, price = "2000")
            type(tokenAmountState, "1")
            fiatAmountState.text.toString() shouldBe "2000"

            // The token amount is what survives a token switch today; the fiat line follows it.
            priceOf(btc.id).value = BigDecimal("100000")
            selectedSrcToken.value = btc
            advanceUntilIdle()

            tokenAmountState.text.toString() shouldBe "1"
            fiatAmountState.text.toString() shouldBe "100000"
        }

    @Test
    fun `entering fiat mode re-seeds the mirror at the current price`() =
        runTest(mainDispatcher) {
            val input = start(backgroundScope, eth, price = "2000")
            type(tokenAmountState, "1")
            fiatAmountState.text.toString() shouldBe "2000"

            // The mirror is off screen in token mode, so a price move is not chased there…
            priceOf(eth.id).value = BigDecimal("2500")
            advanceUntilIdle()
            fiatAmountState.text.toString() shouldBe "2000"

            // …but the field must open on what the token is worth now, not what it was worth
            // when the amount was typed.
            input.toggle()
            advanceUntilIdle()

            fiatAmountState.text.toString() shouldBe "2500"
            tokenAmountState.text.toString() shouldBe "1"
        }

    @Test
    fun `a currency change re-derives the fiat mirror in the new currency`() =
        runTest(mainDispatcher) {
            start(backgroundScope, eth, price = "2000")
            type(tokenAmountState, "1")
            fiatAmountState.text.toString() shouldBe "2000"

            // The symbol follows the currency, so the amount beside it must too — or a EUR
            // symbol would lead a USD figure.
            priceOf(eth.id, AppCurrency.EUR).value = BigDecimal("1800")
            currency.value = AppCurrency.EUR
            advanceUntilIdle()

            fiatAmountState.text.toString() shouldBe "1800"
            uiState.value.fiatSymbol shouldBe "€"
        }

    @Test
    fun `fiat mode is offered only while the source token has a price`() =
        runTest(mainDispatcher) {
            val input = start(backgroundScope, eth, price = "0")
            uiState.value.isSrcFiatInputAvailable.shouldBeFalse()

            // Nothing to convert by: the tap is ignored.
            input.toggle()
            uiState.value.isSrcFiatInput.shouldBeFalse()

            priceOf(eth.id).value = BigDecimal("2000")
            advanceUntilIdle()
            uiState.value.isSrcFiatInputAvailable.shouldBeTrue()

            input.toggle()
            uiState.value.isSrcFiatInput.shouldBeTrue()

            // The price going away leaves fiat mode rather than stranding a field whose
            // conversion can't run.
            priceOf(eth.id).value = BigDecimal.ZERO
            advanceUntilIdle()
            uiState.value.isSrcFiatInputAvailable.shouldBeFalse()
            uiState.value.isSrcFiatInput.shouldBeFalse()
        }

    @Test
    fun `toggle flips fiat mode back to token input`() =
        runTest(mainDispatcher) {
            val input = start(backgroundScope, eth, price = "2000")

            input.toggle()
            uiState.value.isSrcFiatInput.shouldBeTrue()
            input.toggle()
            uiState.value.isSrcFiatInput.shouldBeFalse()
        }

    @Test
    fun `the fiat symbol follows the app currency`() =
        runTest(mainDispatcher) {
            start(backgroundScope, eth, price = "2000")
            uiState.value.fiatSymbol shouldBe "$"

            currency.value = AppCurrency.EUR
            advanceUntilIdle()

            uiState.value.fiatSymbol shouldBe "€"
        }

    @Test
    fun `a failed price read leaves the fields blank instead of stale`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } returns
                flow { throw RuntimeException("network") }
            start(backgroundScope, eth, price = "2000")

            type(fiatAmountState, "1000")

            tokenAmountState.text.toString() shouldBe ""
            uiState.value.isSrcFiatInputAvailable.shouldBeFalse()
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

    private fun priceOf(
        tokenId: String,
        currency: AppCurrency = AppCurrency.USD,
    ): MutableStateFlow<BigDecimal> =
        prices.getOrPut(tokenId to currency) { MutableStateFlow(BigDecimal.ZERO) }

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
