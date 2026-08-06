@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.passcode

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeUnlockResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [PasscodeLockViewModel]. */
internal class PasscodeLockViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var passcodeRepository: PasscodeRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        passcodeRepository = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PasscodeLockViewModel(passcodeRepository)

    /**
     * Types [text] into the field the way the UI would. The view model observes the field through
     * `snapshotFlow`, which only re-emits once snapshot changes are published — without this the
     * second and later edits in a test are silently invisible to it.
     */
    private fun TextFieldState.type(text: String) {
        setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `does not verify until all five digits are entered`() = runTest {
        val model = viewModel()

        model.textFieldState.type("1234")
        advanceUntilIdle()

        coVerify(exactly = 0) { passcodeRepository.unlock(any()) }
    }

    @Test
    fun `verifies as soon as the fifth digit lands`() = runTest {
        coEvery { passcodeRepository.unlock("12345") } returns PasscodeUnlockResult.Success
        val model = viewModel()

        model.textFieldState.type("12345")
        advanceUntilIdle()

        coVerify { passcodeRepository.unlock("12345") }
        assertNull(model.state.value.error)
    }

    @Test
    fun `a wrong passcode clears the field and reports the attempts left`() = runTest {
        coEvery { passcodeRepository.unlock(any()) } returns PasscodeUnlockResult.Wrong(3)
        val model = viewModel()

        model.textFieldState.type("00000")
        advanceUntilIdle()

        assertEquals(PasscodeLockError.Wrong(3), model.state.value.error)
        assertEquals("", model.textFieldState.text.toString())
        assertTrue(model.state.value.isInputEnabled)
    }

    @Test
    fun `the error clears once the user starts typing again`() = runTest {
        coEvery { passcodeRepository.unlock(any()) } returns PasscodeUnlockResult.Wrong(3)
        val model = viewModel()
        model.textFieldState.type("00000")
        advanceUntilIdle()

        model.textFieldState.type("1")
        advanceUntilIdle()

        assertNull(model.state.value.error)
    }

    @Test
    fun `a lockout disables input and counts down to zero`() = runTest {
        coEvery { passcodeRepository.unlock(any()) } returns PasscodeUnlockResult.LockedOut(3_000L)
        val model = viewModel()

        model.textFieldState.type("00000")
        // runCurrent, not advanceUntilIdle: the latter would fast-forward straight through the
        // countdown's delays and land on the cleared state, testing nothing.
        runCurrent()

        assertEquals(PasscodeLockError.LockedOut(3), model.state.value.error)
        assertFalse(model.state.value.isInputEnabled)

        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(PasscodeLockError.LockedOut(2), model.state.value.error)

        advanceTimeBy(2_000)
        runCurrent()
        assertNull(model.state.value.error)
        assertTrue(model.state.value.isInputEnabled)
    }

    @Test
    fun `input stays disabled while an attempt is in flight`() = runTest {
        coEvery { passcodeRepository.unlock(any()) } coAnswers
            {
                delay(1_000)
                PasscodeUnlockResult.Success
            }
        val model = viewModel()

        model.textFieldState.type("12345")
        advanceTimeBy(100)

        assertTrue(model.state.value.isVerifying)
        assertFalse(model.state.value.isInputEnabled)
    }
}
