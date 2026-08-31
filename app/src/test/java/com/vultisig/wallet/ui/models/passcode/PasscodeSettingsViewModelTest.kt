@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.passcode

import com.vultisig.wallet.data.passcode.AutoLockRepository
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.ui.components.BiometricUnlockLauncher
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import javax.crypto.Cipher
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [PasscodeSettingsViewModel]. */
internal class PasscodeSettingsViewModelTest {

    private val passcodeState = MutableStateFlow<PasscodeState>(PasscodeState.Disabled)
    private val autoLockTimeout = MutableStateFlow(AutoLockTimeout.Default)
    private val biometricEnabled = MutableStateFlow(false)

    private lateinit var navigator: Navigator<Destination>
    private lateinit var passcodeRepository: PasscodeRepository
    private lateinit var autoLockRepository: AutoLockRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        navigator = mockk(relaxed = true)
        passcodeRepository = mockk(relaxed = true)
        autoLockRepository = mockk(relaxed = true)
        biometricEnabled.value = false
        every { passcodeRepository.state } returns passcodeState
        every { passcodeRepository.isBiometricUnlockEnabled } returns biometricEnabled
        every { autoLockRepository.timeout } returns autoLockTimeout
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        PasscodeSettingsViewModel(navigator, passcodeRepository, autoLockRepository)

    @Test
    fun `the switch is off when no passcode is configured`() = runTest {
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.state.value.isPasscodeEnabled)
    }

    @Test
    fun `a locked app still reads as having a passcode configured`() = runTest {
        // The settings screen asks "is one set", not "is it entered" — otherwise the switch would
        // flip itself off whenever the app re-locked.
        passcodeState.value = PasscodeState.Locked
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.state.value.isPasscodeEnabled)
    }

    @Test
    fun `a key-unavailable vault does not read as having a passcode configured`() = runTest {
        // There are no credentials to prove, so offering the switch as "on" would present an
        // off-ramp that cannot work.
        passcodeState.value = PasscodeState.KeyUnavailable
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.state.value.isPasscodeEnabled)
    }

    @Test
    fun `the auto-lock row follows the stored timeout`() = runTest {
        autoLockTimeout.value = AutoLockTimeout.FifteenMinutes
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(AutoLockTimeout.FifteenMinutes, model.state.value.autoLockTimeout)
    }

    @Test
    fun `the switch does nothing until the persisted state has been read`() = runTest {
        // While the state is Unknown the switch reads as off even when a passcode exists, so acting
        // on it would route to Set and try to replace the wrap the stored keyshares depend on.
        passcodeState.value = PasscodeState.Unknown
        val model = viewModel()
        advanceUntilIdle()

        assertFalse(model.state.value.isReady)
        model.onPasscodeEnabledChange(true)
        advanceUntilIdle()

        coVerify(exactly = 0) { navigator.route(any<Route.PasscodeEntry>()) }
    }

    @Test
    fun `turning the switch on routes to the set prompt`() = runTest {
        val model = viewModel()
        advanceUntilIdle()

        model.onPasscodeEnabledChange(true)
        advanceUntilIdle()

        coVerify { navigator.route(Route.PasscodeEntry(Route.PasscodeEntryAction.Set)) }
    }

    @Test
    fun `turning the switch off routes to the disable prompt rather than acting immediately`() =
        runTest {
            passcodeState.value = PasscodeState.Unlocked
            val model = viewModel()
            advanceUntilIdle()

            model.onPasscodeEnabledChange(false)
            advanceUntilIdle()

            coVerify { navigator.route(Route.PasscodeEntry(Route.PasscodeEntryAction.Disable)) }
            coVerify(exactly = 0) { passcodeRepository.disablePasscode(any()) }
        }

    @Test
    fun `the biometric switch follows the stored copy`() = runTest {
        passcodeState.value = PasscodeState.Unlocked
        val model = viewModel()
        advanceUntilIdle()
        assertFalse(model.state.value.isBiometricUnlockEnabled)

        biometricEnabled.value = true
        advanceUntilIdle()

        assertTrue(model.state.value.isBiometricUnlockEnabled)
    }

    @Test
    fun `turning biometrics on stores the copy with the cipher the prompt authorised`() = runTest {
        val offered = cipher()
        val authorized = cipher()
        coEvery { passcodeRepository.biometricEnableCipher() } returns offered
        coEvery { passcodeRepository.enableBiometricUnlock(any()) } returns true
        val model = viewModel()
        advanceUntilIdle()

        model.onBiometricUnlockChange(true, BiometricUnlockLauncher { authorized })
        advanceUntilIdle()

        coVerify(exactly = 1) { passcodeRepository.enableBiometricUnlock(authorized) }
        assertNull(model.state.value.biometricError)
    }

    @Test
    fun `a cancelled enable prompt stores nothing and says nothing`() = runTest {
        coEvery { passcodeRepository.biometricEnableCipher() } returns cipher()
        val model = viewModel()
        advanceUntilIdle()

        model.onBiometricUnlockChange(true, BiometricUnlockLauncher { null })
        advanceUntilIdle()

        coVerify(exactly = 0) { passcodeRepository.enableBiometricUnlock(any()) }
        assertNull(model.state.value.biometricError)
    }

    @Test
    fun `a device that cannot mint the key reports it rather than failing silently`() = runTest {
        coEvery { passcodeRepository.biometricEnableCipher() } returns null
        val model = viewModel()
        advanceUntilIdle()

        model.onBiometricUnlockChange(true, BiometricUnlockLauncher { cipher() })
        advanceUntilIdle()

        assertNotNull(model.state.value.biometricError)
        coVerify(exactly = 0) { passcodeRepository.enableBiometricUnlock(any()) }
    }

    @Test
    fun `a copy that did not reach the disk is reported`() = runTest {
        coEvery { passcodeRepository.biometricEnableCipher() } returns cipher()
        coEvery { passcodeRepository.enableBiometricUnlock(any()) } returns false
        val model = viewModel()
        advanceUntilIdle()

        model.onBiometricUnlockChange(true, BiometricUnlockLauncher { cipher() })
        advanceUntilIdle()

        assertNotNull(model.state.value.biometricError)
    }

    @Test
    fun `turning biometrics off needs no prompt`() = runTest {
        biometricEnabled.value = true
        val model = viewModel()
        advanceUntilIdle()

        var prompted = false
        model.onBiometricUnlockChange(
            false,
            BiometricUnlockLauncher {
                prompted = true
                it
            },
        )
        advanceUntilIdle()

        // Removing a copy is not reading it, so nothing has to be authorised to do it.
        assertFalse(prompted)
        coVerify(exactly = 1) { passcodeRepository.disableBiometricUnlock() }
    }

    /** A real instance, uninitialised: these tests are about sequencing, not about the JCE. */
    private fun cipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")
}
