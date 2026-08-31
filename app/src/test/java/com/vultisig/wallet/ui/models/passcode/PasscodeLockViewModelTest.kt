@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.passcode

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.data.passcode.PasscodeUnlockResult
import com.vultisig.wallet.ui.components.BiometricUnlockLauncher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import javax.crypto.Cipher
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [PasscodeLockViewModel]. */
internal class PasscodeLockViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var passcodeRepository: PasscodeRepository
    private val passcodeState = MutableStateFlow<PasscodeState>(PasscodeState.Unlocked)
    private val biometricEnabled = MutableStateFlow(false)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        passcodeRepository = mockk(relaxed = true)
        passcodeState.value = PasscodeState.Unlocked
        biometricEnabled.value = false
        every { passcodeRepository.state } returns passcodeState
        every { passcodeRepository.isBiometricUnlockEnabled } returns biometricEnabled
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() =
        PasscodeLockViewModel(
            passcodeRepository = passcodeRepository,
            // The lockout countdown reads a monotonic clock; the scheduler's virtual time is the
            // one that advances in step with the delays this test fast-forwards through.
            elapsedRealtimeMillis = { testScheduler.currentTime },
        )

    @Test
    fun `re-locking wipes whatever the previous prompt was left holding`() = runTest {
        // The guard hosts this view model outside the nav graph, so it is activity-scoped and one
        // instance serves every lock. A rejected attempt must not still be on screen next time.
        coEvery { passcodeRepository.unlock(any()) } returns PasscodeUnlockResult.Wrong(3)
        val model = viewModel()
        model.textFieldState.type("000000")
        advanceUntilIdle()
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        model.textFieldState.type("12")
        advanceUntilIdle()

        passcodeState.value = PasscodeState.Locked
        advanceUntilIdle()

        assertEquals("", model.textFieldState.text.toString())
        assertNull(model.state.value.error)
    }

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
    fun `does not verify until all six digits are entered`() = runTest {
        val model = viewModel()

        model.textFieldState.type("12345")
        advanceUntilIdle()

        coVerify(exactly = 0) { passcodeRepository.unlock(any()) }
    }

    @Test
    fun `verifies as soon as the sixth digit lands`() = runTest {
        coEvery { passcodeRepository.unlock("123456") } returns PasscodeUnlockResult.Success
        val model = viewModel()

        model.textFieldState.type("123456")
        advanceUntilIdle()

        coVerify { passcodeRepository.unlock("123456") }
        assertNull(model.state.value.error)
    }

    @Test
    fun `a wrong passcode clears the field and reports the attempts left`() = runTest {
        coEvery { passcodeRepository.unlock(any()) } returns PasscodeUnlockResult.Wrong(3)
        val model = viewModel()

        model.textFieldState.type("000000")
        advanceUntilIdle()

        assertEquals(PasscodeLockError.Wrong(3), model.state.value.error)
        assertEquals("", model.textFieldState.text.toString())
        assertTrue(model.state.value.isInputEnabled)
    }

    @Test
    fun `the error survives the field being cleared by the failure itself`() = runTest {
        // The view model clears the field on a wrong passcode, and that clear is itself an emission
        // from the field's flow. If the collector treats it like the user starting over, the error
        // is wiped in the same frame it appears and nothing is ever shown.
        coEvery { passcodeRepository.unlock(any()) } returns PasscodeUnlockResult.Wrong(3)
        val model = viewModel()

        model.textFieldState.type("000000")
        advanceUntilIdle()
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals(PasscodeLockError.Wrong(3), model.state.value.error)
    }

    @Test
    fun `the error clears once the user starts typing again`() = runTest {
        coEvery { passcodeRepository.unlock(any()) } returns PasscodeUnlockResult.Wrong(3)
        val model = viewModel()
        model.textFieldState.type("000000")
        advanceUntilIdle()

        model.textFieldState.type("1")
        advanceUntilIdle()

        assertNull(model.state.value.error)
    }

    @Test
    fun `letters from a hardware keyboard are rejected instead of killing the process`() = runTest {
        // KeyboardType is only an IME hint, so a hardware keyboard can put letters in the field.
        // Passing them on trips requireValidPasscode, whose exception escapes the collector.
        val model = viewModel()

        model.textFieldState.type("12345a")
        advanceUntilIdle()

        coVerify(exactly = 0) { passcodeRepository.unlock(any()) }
        assertEquals(PasscodeLockError.NotDigits, model.state.value.error)
        assertEquals("", model.textFieldState.text.toString())
    }

    @Test
    fun `a lockout disables input and counts down to zero`() = runTest {
        coEvery { passcodeRepository.unlock(any()) } returns PasscodeUnlockResult.LockedOut(3_000L)
        val model = viewModel()

        model.textFieldState.type("000000")
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

        model.textFieldState.type("123456")
        advanceTimeBy(100)

        assertTrue(model.state.value.isVerifying)
        assertFalse(model.state.value.isInputEnabled)
    }

    @Test
    fun `the biometric shortcut is offered only when a copy exists`() = runTest {
        val model = viewModel()
        advanceUntilIdle()
        assertFalse(model.state.value.isBiometricUnlockEnabled)

        biometricEnabled.value = true
        advanceUntilIdle()

        assertTrue(model.state.value.isBiometricUnlockEnabled)
    }

    @Test
    fun `biometrics never fire on their own`() = runTest {
        // The whole point of the change: a passcode user chose the passcode, and a prompt that
        // opens by itself unlocks the app for whoever is holding the phone.
        biometricEnabled.value = true

        viewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { passcodeRepository.biometricUnlockCipher() }
        coVerify(exactly = 0) { passcodeRepository.unlockWithBiometrics(any()) }
    }

    @Test
    fun `a tap runs the prompt and unlocks with the cipher it authorised`() = runTest {
        val offered = cipher()
        val authorized = cipher()
        coEvery { passcodeRepository.biometricUnlockCipher() } returns offered
        coEvery { passcodeRepository.unlockWithBiometrics(any()) } returns
            PasscodeUnlockResult.Success
        val model = viewModel()

        model.onUseBiometricsClick(launcherReturning(authorized))
        advanceUntilIdle()

        // The authorised instance, not the one handed to the prompt: only that one carries the
        // authorisation the keystore granted.
        coVerify(exactly = 1) { passcodeRepository.unlockWithBiometrics(authorized) }
        assertNull(model.state.value.error)
    }

    @Test
    fun `a cancelled prompt says nothing and leaves the passcode field alone`() = runTest {
        coEvery { passcodeRepository.biometricUnlockCipher() } returns cipher()
        val model = viewModel()

        model.onUseBiometricsClick(launcherReturning(null))
        advanceUntilIdle()

        assertNull(model.state.value.error)
        coVerify(exactly = 0) { passcodeRepository.unlockWithBiometrics(any()) }
        assertTrue(model.state.value.isInputEnabled)
    }

    @Test
    fun `a copy that has gone is reported rather than failing silently`() = runTest {
        coEvery { passcodeRepository.biometricUnlockCipher() } returns null
        val model = viewModel()

        model.onUseBiometricsClick(launcherReturning(cipher()))
        advanceUntilIdle()

        assertEquals(PasscodeLockError.BiometricUnavailable, model.state.value.error)
    }

    @Test
    fun `a match that still does not open the app is reported`() = runTest {
        coEvery { passcodeRepository.biometricUnlockCipher() } returns cipher()
        coEvery { passcodeRepository.unlockWithBiometrics(any()) } returns
            PasscodeUnlockResult.Failed
        val model = viewModel()

        model.onUseBiometricsClick(launcherReturning(cipher()))
        advanceUntilIdle()

        assertEquals(PasscodeLockError.BiometricUnavailable, model.state.value.error)
    }

    @Test
    fun `a second tap while the prompt is up starts nothing`() = runTest {
        coEvery { passcodeRepository.biometricUnlockCipher() } returns cipher()
        val model = viewModel()
        val launcher = BiometricUnlockLauncher {
            delay(1_000)
            null
        }

        model.onUseBiometricsClick(launcher)
        advanceTimeBy(100)
        model.onUseBiometricsClick(launcher)
        advanceUntilIdle()

        coVerify(exactly = 1) { passcodeRepository.biometricUnlockCipher() }
    }

    /** A real instance, uninitialised: these tests are about sequencing, not about the JCE. */
    private fun cipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    private fun launcherReturning(result: Cipher?) = BiometricUnlockLauncher { result }
}
