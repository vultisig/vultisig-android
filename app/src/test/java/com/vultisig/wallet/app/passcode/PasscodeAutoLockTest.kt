@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.app.passcode

import androidx.lifecycle.LifecycleOwner
import com.vultisig.wallet.data.passcode.AutoLockRepository
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.data.passcode.PasscodeRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [PasscodeAutoLock]. */
internal class PasscodeAutoLockTest {

    private lateinit var passcodeRepository: PasscodeRepository
    private lateinit var autoLockRepository: AutoLockRepository
    private lateinit var owner: LifecycleOwner
    private val timeout = MutableStateFlow(AutoLockTimeout.Default)
    private var elapsedRealtime = 100_000L

    @BeforeEach
    fun setUp() {
        passcodeRepository = mockk(relaxed = true)
        autoLockRepository = mockk(relaxed = true)
        owner = mockk(relaxed = true)
        every { autoLockRepository.timeout } returns timeout
        elapsedRealtime = 100_000L
    }

    private fun autoLock(scope: CoroutineScope) =
        PasscodeAutoLock(
            passcodeRepository = passcodeRepository,
            autoLockRepository = autoLockRepository,
            elapsedRealtimeMillis = { elapsedRealtime },
            parentScope = scope,
        )

    /**
     * Starts the mirror of the persisted timeout without registering a real lifecycle. Runs in
     * [TestScope.backgroundScope] because the timeout collector never completes, and a never-ending
     * child of the test scope itself would hang `runTest`.
     */
    private fun TestScope.started(): PasscodeAutoLock =
        autoLock(backgroundScope).also { it.start(mockk(relaxed = true)) }.also { runCurrent() }

    @Test
    fun `the default timeout locks the moment the app is backgrounded`() = runTest {
        val autoLock = started()

        autoLock.onStop(owner)

        verify { passcodeRepository.lock() }
    }

    @Test
    fun `a timed lock does not fire before the timeout elapses`() = runTest {
        timeout.value = AutoLockTimeout.FiveMinutes
        val autoLock = started()

        autoLock.onStop(owner)
        advanceTimeBy(4 * 60_000L)
        runCurrent()

        verify(exactly = 0) { passcodeRepository.lock() }
    }

    @Test
    fun `a timed lock fires once the timeout elapses in the background`() = runTest {
        timeout.value = AutoLockTimeout.OneMinute
        val autoLock = started()

        autoLock.onStop(owner)
        advanceTimeBy(60_001L)
        runCurrent()

        verify { passcodeRepository.lock() }
    }

    @Test
    fun `returning before the timeout cancels the pending lock`() = runTest {
        timeout.value = AutoLockTimeout.FiveMinutes
        val autoLock = started()

        autoLock.onStop(owner)
        advanceTimeBy(60_000L)
        elapsedRealtime += 60_000L
        autoLock.onStart(owner)
        advanceTimeBy(10 * 60_000L)
        runCurrent()

        verify(exactly = 0) { passcodeRepository.lock() }
    }

    @Test
    fun `a process frozen past its timeout still locks on return`() = runTest {
        // The scheduled job never got to run, so the elapsed-time check is the only thing standing
        // between a frozen process and an unlocked app.
        timeout.value = AutoLockTimeout.FiveMinutes
        val autoLock = started()

        autoLock.onStop(owner)
        elapsedRealtime += 6 * 60_000L
        autoLock.onStart(owner)
        runCurrent()

        verify { passcodeRepository.lock() }
    }

    @Test
    fun `a wound-back clock cannot shorten the away time`() = runTest {
        // elapsedRealtime is monotonic, so a backwards jump yields a negative delta rather than a
        // spuriously large one. Either way it must not read as "long enough to stay unlocked".
        timeout.value = AutoLockTimeout.FiveMinutes
        val autoLock = started()

        autoLock.onStop(owner)
        elapsedRealtime -= 60 * 60_000L
        autoLock.onStart(owner)
        runCurrent()

        verify(exactly = 0) { passcodeRepository.lock() }
    }

    @Test
    fun `returning without having been backgrounded does not lock`() = runTest {
        timeout.value = AutoLockTimeout.FiveMinutes
        val autoLock = started()

        autoLock.onStart(owner)
        runCurrent()

        verify(exactly = 0) { passcodeRepository.lock() }
    }

    @Test
    fun `changing the timeout takes effect without a restart`() = runTest {
        timeout.value = AutoLockTimeout.FiveMinutes
        val autoLock = started()

        timeout.value = AutoLockTimeout.Immediate
        runCurrent()
        autoLock.onStop(owner)

        verify { passcodeRepository.lock() }
    }
}
