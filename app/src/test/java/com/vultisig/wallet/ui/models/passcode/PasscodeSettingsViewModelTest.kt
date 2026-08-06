@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.passcode

import com.vultisig.wallet.data.passcode.AutoLockRepository
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private lateinit var navigator: Navigator<Destination>
    private lateinit var passcodeRepository: PasscodeRepository
    private lateinit var autoLockRepository: AutoLockRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        navigator = mockk(relaxed = true)
        passcodeRepository = mockk(relaxed = true)
        autoLockRepository = mockk(relaxed = true)
        every { passcodeRepository.state } returns passcodeState
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
    fun `the auto-lock row follows the stored timeout`() = runTest {
        autoLockTimeout.value = AutoLockTimeout.FifteenMinutes
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(AutoLockTimeout.FifteenMinutes, model.state.value.autoLockTimeout)
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
}
