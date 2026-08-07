@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.passcode

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeUnlockResult
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.PasscodeEntryAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [PasscodeEntryViewModel]. */
internal class PasscodeEntryViewModelTest {

    private lateinit var passcodeRepository: PasscodeRepository
    private lateinit var navigator: Navigator<Destination>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        passcodeRepository = mockk(relaxed = true)
        navigator = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    /**
     * Builds the view model for [action]. `toRoute` decodes through an android Bundle, which is not
     * available to a plain JVM test, so the extension is stubbed rather than fed real arguments.
     */
    private fun TestScope.viewModel(action: PasscodeEntryAction): PasscodeEntryViewModel {
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.PasscodeEntry>() } returns
            Route.PasscodeEntry(action)
        return PasscodeEntryViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            passcodeRepository = passcodeRepository,
            // The lockout countdown reads a monotonic clock; the scheduler's virtual time is the
            // one that advances in step with the delays this test fast-forwards through.
            elapsedRealtimeMillis = { testScheduler.currentTime },
        )
    }

    /**
     * Types [text] the way a user would, one publish at a time.
     *
     * The view model clears the field between steps, and `snapshotFlow` skips a value equal to the
     * one it last emitted — so the clear has to be published and observed before the next entry,
     * otherwise re-entering the same digits looks like no change at all. On a real device the frame
     * between keystrokes does this for free.
     */
    private fun TestScope.type(field: TextFieldState, text: String) {
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        field.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
    }

    @Test
    fun `setting a passcode asks twice and stores the confirmed value`() = runTest {
        val model = viewModel(PasscodeEntryAction.Set)
        advanceUntilIdle()
        assertEquals(PasscodeEntryStep.New, model.state.value.step)

        type(model.textFieldState, "123456")
        assertEquals(PasscodeEntryStep.Confirm, model.state.value.step)

        type(model.textFieldState, "123456")

        coVerify { passcodeRepository.setPasscode("123456") }
    }

    @Test
    fun `a mismatched confirmation restarts at the choose step`() = runTest {
        val model = viewModel(PasscodeEntryAction.Set)
        advanceUntilIdle()
        type(model.textFieldState, "123456")

        type(model.textFieldState, "654321")

        assertEquals(PasscodeEntryStep.New, model.state.value.step)
        assertEquals(PasscodeEntryError.Mismatch, model.state.value.error)
        coVerify(exactly = 0) { passcodeRepository.setPasscode(any()) }
    }

    @Test
    fun `changing starts by proving the current passcode`() = runTest {
        coEvery { passcodeRepository.unlock("111111") } returns PasscodeUnlockResult.Success
        val model = viewModel(PasscodeEntryAction.Change)
        advanceUntilIdle()
        assertEquals(PasscodeEntryStep.Current, model.state.value.step)

        type(model.textFieldState, "111111")
        assertEquals(PasscodeEntryStep.New, model.state.value.step)

        type(model.textFieldState, "222222")
        type(model.textFieldState, "222222")

        coVerify { passcodeRepository.changePasscode("111111", "222222") }
    }

    @Test
    fun `a wrong current passcode still shows its error after the field self-clears`() = runTest {
        // Rejecting a passcode empties the field, and that empty is another emission from the same
        // flow. Treating it as the user starting over erases the error before it is ever seen.
        coEvery { passcodeRepository.unlock(any()) } returns PasscodeUnlockResult.Wrong(4)
        val model = viewModel(PasscodeEntryAction.Change)
        advanceUntilIdle()

        type(model.textFieldState, "000000")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals(PasscodeEntryError.Wrong(4), model.state.value.error)
    }

    @Test
    fun `a mismatch keeps its message after the field self-clears`() = runTest {
        val model = viewModel(PasscodeEntryAction.Set)
        advanceUntilIdle()
        type(model.textFieldState, "123456")

        type(model.textFieldState, "654321")
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals(PasscodeEntryError.Mismatch, model.state.value.error)
    }

    @Test
    fun `a wrong current passcode does not advance`() = runTest {
        coEvery { passcodeRepository.unlock(any()) } returns PasscodeUnlockResult.Wrong(4)
        val model = viewModel(PasscodeEntryAction.Change)
        advanceUntilIdle()

        type(model.textFieldState, "000000")

        assertEquals(PasscodeEntryStep.Current, model.state.value.step)
        assertEquals(PasscodeEntryError.Wrong(4), model.state.value.error)
    }

    @Test
    fun `disabling completes as soon as the current passcode checks out`() = runTest {
        coEvery { passcodeRepository.unlock("111111") } returns PasscodeUnlockResult.Success
        coEvery { passcodeRepository.disablePasscode("111111") } returns
            PasscodeUnlockResult.Success
        val model = viewModel(PasscodeEntryAction.Disable)
        advanceUntilIdle()

        type(model.textFieldState, "111111")

        coVerify { passcodeRepository.disablePasscode("111111") }
    }

    @Test
    fun `a lockout blocks input and clears when it expires`() = runTest {
        coEvery { passcodeRepository.unlock(any()) } returns PasscodeUnlockResult.LockedOut(2_000L)
        val model = viewModel(PasscodeEntryAction.Disable)
        advanceUntilIdle()

        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        model.textFieldState.setTextAndPlaceCursorAtEnd("000000")
        Snapshot.sendApplyNotifications()
        runCurrent()

        assertEquals(PasscodeEntryError.LockedOut(2), model.state.value.error)
        assertFalse(model.state.value.isInputEnabled)

        advanceUntilIdle()
        assertNull(model.state.value.error)
    }
}
