@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.passcode

import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import io.mockk.coEvery
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

/** Unit tests for [PasscodeGuardViewModel]. */
internal class PasscodeGuardViewModelTest {

    private lateinit var passcodeRepository: PasscodeRepository
    private val state = MutableStateFlow<PasscodeState>(PasscodeState.Unknown)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        passcodeRepository = mockk(relaxed = true)
        every { passcodeRepository.state } returns state
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed initialize does not leave the gate closed over the whole app`() = runTest {
        // initialize decodes keystore-backed credentials, and the keystore can fail. Thrown from a
        // bare launch, that would pin the state at Unknown — and the opaque cover the guard draws
        // while it waits becomes the entire app until it is force-stopped.
        coEvery { passcodeRepository.initialize() } throws IllegalStateException("keystore is gone")

        val model = PasscodeGuardViewModel(passcodeRepository)
        advanceUntilIdle()
        state.value = PasscodeState.Disabled
        advanceUntilIdle()

        assertEquals(PasscodeState.Disabled, model.state.value.passcodeState)
        assertFalse(model.state.value.isGateClosed)
    }

    @Test
    fun `the gate stays closed for every state the user cannot see past`() = runTest {
        val model = PasscodeGuardViewModel(passcodeRepository)
        advanceUntilIdle()

        listOf(
                PasscodeState.Unknown,
                PasscodeState.Locked,
                PasscodeState.KeyUnavailable,
                PasscodeState.StoreUnavailable,
            )
            .forEach { closed ->
                state.value = closed
                advanceUntilIdle()
                assertTrue(model.state.value.isGateClosed, "$closed must close the gate")
            }

        listOf(PasscodeState.Unlocked, PasscodeState.Disabled).forEach { open ->
            state.value = open
            advanceUntilIdle()
            assertFalse(model.state.value.isGateClosed, "$open must open the gate")
        }
    }

    @Test
    fun `an unlock this session is remembered once the passcode is turned off`() = runTest {
        // Disabling flips straight to Disabled, which composes the device-credential gate for the
        // first time this process. The user has just proved who they are; asking again is a non
        // sequitur.
        val model = PasscodeGuardViewModel(passcodeRepository)
        advanceUntilIdle()

        state.value = PasscodeState.Unlocked
        advanceUntilIdle()
        state.value = PasscodeState.Disabled
        advanceUntilIdle()

        assertTrue(model.state.value.isIdentityProven)
    }
}
