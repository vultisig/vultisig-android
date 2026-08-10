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
    fun `a throwing initialize neither escapes nor stops the view model following the repository`() =
        runTest {
            // Belt to the repository's braces: initialize resolves its own store failures to
            // StoreUnavailable, so this stands for anything else that could throw. What it must
            // not do is escape a bare launch and take the process down, or kill the collector
            // that is the only thing left able to move the state off Unknown.
            coEvery { passcodeRepository.initialize() } throws
                IllegalStateException("keystore is gone")

            val model = PasscodeGuardViewModel(passcodeRepository)
            advanceUntilIdle()

            // A mocked repository publishes nothing of its own, so the gate is still closed here.
            // That is the point: only the collector can open it, and it has to have survived.
            assertEquals(PasscodeState.Unknown, model.state.value.passcodeState)
            assertTrue(model.state.value.isGateClosed)

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
    fun `the lock only goes up over a gate that is already closed`() = runTest {
        // Two windows: the lock draws in one of its own, the cover the gate draws is in the
        // activity's. A state that locked without closing the gate would leave the app live behind
        // the lock — walkable by TalkBack, and tappable for the frame before the lock's window is
        // added.
        val model = PasscodeGuardViewModel(passcodeRepository)
        advanceUntilIdle()

        val locking =
            listOf(
                PasscodeState.Locked,
                PasscodeState.KeyUnavailable,
                PasscodeState.StoreUnavailable,
            )
        val notLocking =
            listOf(PasscodeState.Unknown, PasscodeState.Unlocked, PasscodeState.Disabled)

        locking.forEach { locked ->
            state.value = locked
            advanceUntilIdle()
            assertTrue(model.state.value.isLocked, "$locked must read as locked")
            assertTrue(model.state.value.isGateClosed, "$locked must lock behind a closed gate")
        }

        notLocking.forEach { open ->
            state.value = open
            advanceUntilIdle()
            assertFalse(model.state.value.isLocked, "$open must not read as locked")
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
