@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.NearApi
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AmountManagerTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val tokenAmountFieldState = TextFieldState()
    private val fiatAmountFieldState = TextFieldState()
    private val selectedToken = MutableStateFlow<Coin?>(null)
    private val gasFee = MutableStateFlow<TokenValue?>(null)
    private val appCurrency = MutableStateFlow(AppCurrency.USD)
    private var account: Account? = null
    private val chainValidationService =
        ChainValidationService(
            rippleApi = mockk(relaxed = true),
            bittensorApi = mockk(relaxed = true),
            nearApi = mockk<NearApi>(),
        )
    private val tokenPriceRepository: TokenPriceRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ──────── validateTokenAmount ────────

    @Test
    fun `validateTokenAmount empty returns no_amount error`() {
        val manager = build(emptyScope())
        assertEquals(R.string.send_error_no_amount, manager.validateTokenAmount("").stringId())
    }

    @Test
    fun `validateTokenAmount zero returns no_amount error`() {
        val manager = build(emptyScope())
        assertEquals(R.string.send_error_no_amount, manager.validateTokenAmount("0").stringId())
    }

    @Test
    fun `validateTokenAmount non-numeric returns no_amount error`() {
        val manager = build(emptyScope())
        assertEquals(R.string.send_error_no_amount, manager.validateTokenAmount("abc").stringId())
    }

    @Test
    fun `validateTokenAmount rejects scientific notation`() {
        val manager = build(emptyScope())
        // Cannot be typed on the numeric keyboard, but reaches the field unfiltered via a
        // vultisig://send deeplink amount; toBigDecimalOrNull would otherwise accept it.
        assertEquals(R.string.send_error_no_amount, manager.validateTokenAmount("1e5").stringId())
        assertEquals(R.string.send_error_no_amount, manager.validateTokenAmount("1E+2").stringId())
        assertEquals(R.string.send_error_no_amount, manager.validateTokenAmount("2.5e3").stringId())
    }

    @Test
    fun `validateTokenAmount rejects hex notation`() {
        val manager = build(emptyScope())
        assertEquals(R.string.send_error_no_amount, manager.validateTokenAmount("0x1F").stringId())
    }

    @Test
    fun `validateTokenAmount rejects signed values`() {
        val manager = build(emptyScope())
        assertEquals(R.string.send_error_no_amount, manager.validateTokenAmount("-5").stringId())
        assertEquals(R.string.send_error_no_amount, manager.validateTokenAmount("+5").stringId())
    }

    @Test
    fun `validateTokenAmount over-length returns invalid_amount error`() {
        val manager = build(emptyScope())
        val tooLong = "1".repeat(TextFieldUtils.AMOUNT_MAX_LENGTH + 1)
        assertEquals(
            R.string.send_from_invalid_amount,
            manager.validateTokenAmount(tooLong).stringId(),
        )
    }

    @Test
    fun `validateTokenAmount valid returns null`() {
        val manager = build(emptyScope())
        assertNull(manager.validateTokenAmount("1.5"))
    }

    @Test
    fun `validateTokenAmount over-precision returns too_many_decimals error`() {
        selectedToken.value = usdcToken() // 6 decimals
        val manager = build(emptyScope())

        val error = manager.validateTokenAmount("1.1234567") as? UiText.FormattedText

        assertEquals(R.string.send_error_too_many_decimals, error?.resId)
        assertEquals(listOf(6), error?.formatArgs)
    }

    @Test
    fun `validateTokenAmount at exact precision returns null`() {
        selectedToken.value = usdcToken() // 6 decimals
        val manager = build(emptyScope())
        assertNull(manager.validateTokenAmount("1.123456"))
    }

    @Test
    fun `validateTokenAmount ignores trailing zeros beyond precision`() {
        selectedToken.value = usdcToken() // 6 decimals
        val manager = build(emptyScope())
        assertNull(manager.validateTokenAmount("1.5000000"))
    }

    // ──────── markMax ────────

    @Test
    fun `markMax with positive amount sets isMaxAmount and snapshot`() =
        runTest(mainDispatcher) {
            val manager = build(backgroundScope)

            manager.markMax(BigDecimal("0.5"))

            assertTrue(manager.isMaxAmount.value)
            assertEquals(BigDecimal("0.5"), manager.currentMaxAmount)
        }

    @Test
    fun `markMax with zero leaves isMaxAmount false`() =
        runTest(mainDispatcher) {
            val manager = build(backgroundScope)

            manager.markMax(BigDecimal.ZERO)

            assertFalse(manager.isMaxAmount.value)
            assertEquals(BigDecimal.ZERO, manager.currentMaxAmount)
        }

    // ──────── conversion ────────

    @Test
    fun `typing token amount populates fiat amount`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } returns flowOf(BigDecimal("2000"))
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            // 0.5 * 2000 = 1000.00, scale = 18, stripTrailingZeros → "1E+3"
            // toPlainString gives "1000"
            assertEquals("1000", fiatAmountFieldState.text.toString())
        }

    @Test
    fun `typing fiat amount populates token amount`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } returns flowOf(BigDecimal("2000"))
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            // First write to tokenField with empty user-input cache so the conversion runs.
            // Then write to fiatField — different value triggers the fiat→token branch.
            fiatAmountFieldState.setTextAndPlaceCursorAtEnd("1000")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            // 1000 / 2000 = 0.5, rounded down to five significant digits and trimmed.
            assertEquals("0.5", tokenAmountFieldState.text.toString())
            assertEquals("1000", fiatAmountFieldState.text.toString())
        }

    @Test
    fun `a fiat entry rounds the token amount down to five significant digits`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } returns
                flowOf(BigDecimal("2943.37"))
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            fiatAmountFieldState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            // 1 / 2943.37 = 0.000339746…; the typed fiat stays as typed.
            tokenAmountFieldState.text.toString() shouldBe "0.00033974"
            fiatAmountFieldState.text.toString() shouldBe "1"
        }

    @Test
    fun `a DeFi form keeps main's unstripped scale on a fiat entry`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } returns flowOf(BigDecimal("2000"))
            val manager = build(backgroundScope, isPlainSend = false)
            manager.start()
            selectedToken.value = ethToken()

            fiatAmountFieldState.setTextAndPlaceCursorAtEnd("1000")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            // The value main's own test expected before plain send's rounding existed.
            tokenAmountFieldState.text.toString() shouldBe "0.500000000000000000"
        }

    @Test
    fun `a fiat-derived amount equal to the max snapshot is Max, as submit treats it`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } returns flowOf(BigDecimal("2000"))
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()
            manager.markMax(BigDecimal("0.5"))

            fiatAmountFieldState.setTextAndPlaceCursorAtEnd("1000")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            tokenAmountFieldState.text.toString() shouldBe "0.5"
            manager.isMaxAmount.value shouldBe true
        }

    @Test
    fun `rounding keeps five significant digits, never cuts whole units, trims zeros`() {
        val cases =
            listOf(
                "0.000339871234" to "0.00033987",
                "0.00714285714" to "0.0071428",
                "25.000000" to "25",
                "1234.56789" to "1234.5",
                "1234567.89" to "1234567",
                "0.999999" to "0.99999",
                "100000" to "100000",
            )
        cases.forEach { (input, expected) ->
            BigDecimal(input)
                .roundedDownToSignificant(FIAT_DERIVED_SIGNIFICANT_DIGITS, 18)
                .toPlainString() shouldBe expected
        }
    }

    @Test
    fun `rounding never keeps more decimals than the coin has`() {
        BigDecimal("0.000339871234").roundedDownToSignificant(5, 6).toPlainString() shouldBe
            "0.000339"
        BigDecimal("0.0000001").roundedDownToSignificant(5, 6).toPlainString() shouldBe "0"
    }

    @Test
    fun `rounding passes non-positive values through`() {
        BigDecimal.ZERO.roundedDownToSignificant(5, 18) shouldBe BigDecimal.ZERO
    }

    @Test
    fun `rounding stays below the exact value and within one unit of the fifth digit`() {
        val random = java.util.Random(ROUNDING_SEED)
        repeat(ROUNDING_SAMPLES) {
            val exact =
                BigDecimal(java.math.BigInteger(64, random).add(java.math.BigInteger.ONE))
                    .movePointLeft(random.nextInt(30))
            val rounded = exact.roundedDownToSignificant(5, 18)
            val magnitude = exact.precision() - exact.scale() - 1
            val places = (5 - 1 - magnitude).coerceIn(0, 18)
            val step = BigDecimal.ONE.movePointLeft(places)

            (rounded <= exact) shouldBe true
            (exact - rounded < step) shouldBe true
        }
    }

    @Test
    fun `zero price leaves fiat field empty`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } returns flowOf(BigDecimal.ZERO)
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1.0")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals("", fiatAmountFieldState.text.toString())
        }

    @Test
    fun `price-fetch exception leaves fiat field empty`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } throws RuntimeException("network")
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1.0")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals("", fiatAmountFieldState.text.toString())
        }

    @Test
    fun `non-numeric token input clears fiat field without calling price repo`() =
        runTest(mainDispatcher) {
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("abc")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            // convertValue returns "" for non-numeric input; the takeIf gate skips the field write.
            assertEquals("", fiatAmountFieldState.text.toString())
            coVerify(exactly = 0) { tokenPriceRepository.getPrice(any(), any()) }
        }

    /**
     * A submit-time clamp writes both fields itself. Left to the conversion collector, that write
     * reads as typing and re-runs the price fetch — which blanks the fiat mirror when it fails, so
     * a resubmit without retyping would carry a $0.00 estimate beside a correct amount.
     */
    @Test
    fun `markProgrammaticAmount keeps a failed price fetch from blanking the written fiat`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } throws RuntimeException("network")
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            manager.markProgrammaticAmount("0.9985", "1997")
            // One atomic apply, as DefaultSendStrategy.showAdjustedAmount does it — writing the
            // two fields separately exposes a half-updated pair that the collector reads as a
            // cleared field, and it then clears the counterpart.
            Snapshot.withMutableSnapshot {
                tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.9985")
                fiatAmountFieldState.setTextAndPlaceCursorAtEnd("1997")
            }
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals("0.9985", tokenAmountFieldState.text.toString())
            assertEquals("1997", fiatAmountFieldState.text.toString())
            coVerify(exactly = 0) { tokenPriceRepository.getPrice(any(), any()) }
        }

    @Test
    fun `markProgrammaticAmount re-derives isMaxAmount against the max snapshot`() =
        runTest(mainDispatcher) {
            val manager = build(backgroundScope)
            manager.markMax(BigDecimal("0.9985"))

            manager.markProgrammaticAmount("0.9985", null)
            assertTrue(manager.isMaxAmount.value)

            // A clamp that lands below the snapshot is no longer the maximum.
            manager.markProgrammaticAmount("0.5", null)
            assertFalse(manager.isMaxAmount.value)
        }

    @Test
    fun `resetUserInputCache lets a token switch re-run conversion on the same amount`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } returns flowOf(BigDecimal("100"))
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()
            assertEquals("100", fiatAmountFieldState.text.toString())

            // Mirror the VM behavior on a token swap: clear the cache, then push a new token
            // through selectedToken. Without resetUserInputCache the cached "1" would block the
            // re-conversion against the new token's price; with it, conversion must run again.
            manager.resetUserInputCache()
            selectedToken.value = polkadotToken()
            advanceUntilIdle()

            // Pin the behavior: the price lookup must have been invoked a second time. Without
            // this assertion the test would still pass even if resetUserInputCache no longer
            // cleared the user-input cache — the fiat field already held "100" from run 1.
            coVerify(exactly = 2) { tokenPriceRepository.getPrice(any(), any()) }
        }

    @Test
    fun `resetUserInputCache clears the max snapshot to avoid carry-over across token switches`() =
        runTest(mainDispatcher) {
            val manager = build(backgroundScope)

            manager.markMax(BigDecimal("0.5"))
            assertTrue(manager.isMaxAmount.value)
            assertEquals(BigDecimal("0.5"), manager.currentMaxAmount)

            // Simulating selectToken: the VM calls resetUserInputCache. The max snapshot from
            // the previous token must NOT survive — otherwise an unrelated amount matching the
            // old max would set isMaxAmount=true on the new token.
            manager.resetUserInputCache()

            assertFalse(manager.isMaxAmount.value)
            assertEquals(BigDecimal.ZERO, manager.currentMaxAmount)
        }

    // ──────── reaping flow ────────

    @Test
    fun `reapingError stays null until all three inputs are present`() =
        runTest(mainDispatcher) {
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()
            // Token + amount but no gas fee yet — combine must not emit.

            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1.0")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertNull(manager.reapingError.value)
        }

    @Test
    fun `reapingError mirrors chain validation result once all inputs present`() =
        runTest(mainDispatcher) {
            // Use Polkadot — that's a chain ChainValidationService actually checks for reaping.
            val polkadot = polkadotToken()
            val polkadotAccount =
                Account(
                    token = polkadot,
                    tokenValue = TokenValue(value = BigInteger("1000000000"), token = polkadot),
                    fiatValue = null,
                    price = null,
                )
            account = polkadotAccount

            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = polkadot
            // Gas fee that would leave balance < existential deposit → reaping warning.
            gasFee.value = TokenValue(value = BigInteger("999999999"), token = polkadot)

            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.000000001")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            // We don't assert the exact UiText — that's ChainValidationService's job to test —
            // but we do assert the manager wired the inputs through and surfaced the result.
            // For Polkadot+DOT with under-existential remainder, the service returns non-null.
            assertEquals(
                UiText.StringResource(R.string.send_form_polka_reaping_warning),
                manager.reapingError.value,
            )
        }

    private fun build(scope: CoroutineScope, isPlainSend: Boolean = true) =
        AmountManager(
            scope = scope,
            tokenAmountFieldState = tokenAmountFieldState,
            fiatAmountFieldState = fiatAmountFieldState,
            selectedToken = selectedToken,
            gasFee = gasFee,
            accountProvider = { account },
            appCurrency = appCurrency,
            chainValidationService = chainValidationService,
            tokenPriceRepository = tokenPriceRepository,
            roundsFiatDerivedAmount = { isPlainSend },
        )

    /** Empty scope for tests that only call pure helpers (no flow plumbing). */
    private fun emptyScope(): CoroutineScope = CoroutineScope(mainDispatcher)

    private fun ethToken(): Coin =
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

    private fun polkadotToken(): Coin =
        Coin(
            chain = Chain.Polkadot,
            ticker = "DOT",
            logo = "",
            address = "1polkadot",
            decimal = 10,
            hexPublicKey = "",
            priceProviderID = "polkadot",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun usdcToken(): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = "0xself",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "usd-coin",
            contractAddress = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            isNativeToken = false,
        )

    /** Convenience: pull the StringResource id out of a UiText for terse assertions. */
    private fun UiText?.stringId(): Int? = (this as? UiText.StringResource)?.resId

    private companion object {
        const val ROUNDING_SEED = 5L
        const val ROUNDING_SAMPLES = 2_000
    }
}
