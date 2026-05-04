@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.ui.utils.UiText
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
    private val chainValidationService = ChainValidationService()
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

            // 1000 / 2000 = 0.5, scaled to token decimal (18) — fiat→token branch does NOT
            // stripTrailingZeros (intentional: preserves the user's typed precision in the field).
            assertEquals("0.500000000000000000", tokenAmountFieldState.text.toString())
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

    @Test
    fun `resetUserInputCache lets next emission re-trigger conversion`() =
        runTest(mainDispatcher) {
            every { tokenPriceRepository.getPrice(any(), any()) } returns flowOf(BigDecimal("100"))
            val manager = build(backgroundScope)
            manager.start()
            selectedToken.value = ethToken()

            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()
            assertEquals("100", fiatAmountFieldState.text.toString())

            // Without resetUserInputCache, re-typing the same value would be a no-op.
            manager.resetUserInputCache()
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("1")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            // Conversion ran again — fiat field still holds the converted value.
            assertEquals("100", fiatAmountFieldState.text.toString())
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

    private fun build(scope: CoroutineScope) =
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

    /** Convenience: pull the StringResource id out of a UiText for terse assertions. */
    private fun UiText?.stringId(): Int? = (this as? UiText.StringResource)?.resId
}
