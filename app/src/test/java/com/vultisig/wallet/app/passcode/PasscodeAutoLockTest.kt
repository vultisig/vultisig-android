@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.app.passcode

import androidx.lifecycle.LifecycleOwner
import com.vultisig.wallet.data.passcode.AutoLockHold
import com.vultisig.wallet.data.passcode.AutoLockRepository
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.data.passcode.PasscodeRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

    private val autoLockHold = AutoLockHold()

    private fun autoLock(scope: CoroutineScope) =
        PasscodeAutoLock(
            passcodeRepository = passcodeRepository,
            autoLockRepository = autoLockRepository,
            autoLockHold = autoLockHold,
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
    fun `the default timeout never locks on its own`() = runTest {
        // Matches the Windows client: auto-lock is off until the user turns it on.
        val autoLock = started()

        autoLock.onStop(owner)
        advanceTimeBy(24 * 60 * 60_000L)
        runCurrent()

        verify(exactly = 0) { passcodeRepository.lock() }
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
    fun `starting twice does not register a second timeout collector`() = runTest {
        // MainActivity.onCreate runs again on every configuration change and calls start each
        // time. Without the guard each pass leaks another collector into the process singleton.
        val lifecycle = mockk<androidx.lifecycle.Lifecycle>(relaxed = true)
        val autoLock = autoLock(backgroundScope)

        autoLock.start(lifecycle)
        autoLock.start(lifecycle)
        runCurrent()

        verify(exactly = 1) { lifecycle.addObserver(autoLock) }
    }

    @Test
    fun `a timeout raised while away is still applied on return`() = runTest {
        // The stamp is taken before the timeout is consulted, so a change made while the app is
        // away still has something for the elapsed-time check on return to compare against.
        val autoLock = started()

        autoLock.onStop(owner)
        timeout.value = AutoLockTimeout.FiveMinutes
        runCurrent()
        elapsedRealtime += 10 * 60_000L
        autoLock.onStart(owner)
        runCurrent()

        verify { passcodeRepository.lock() }
    }

    @Test
    fun `a held lock waits for the ceremony to finish`() = runTest {
        // Keygen cannot be paused: locking before it writes its keyshare destroys that share.
        timeout.value = AutoLockTimeout.OneMinute
        val autoLock = started()
        val released = CompletableDeferred<Unit>()
        backgroundScope.launch { autoLockHold.withHold { released.await() } }
        runCurrent()

        autoLock.onStop(owner)
        advanceTimeBy(2 * 60_000L)
        runCurrent()
        verify(exactly = 0) { passcodeRepository.lock() }

        released.complete(Unit)
        runCurrent()
        verify { passcodeRepository.lock() }
    }

    @Test
    fun `changing the timeout takes effect without a restart`() = runTest {
        timeout.value = AutoLockTimeout.ThirtyMinutes
        val autoLock = started()

        timeout.value = AutoLockTimeout.OneMinute
        runCurrent()
        autoLock.onStop(owner)
        advanceTimeBy(60_000L + 1)
        runCurrent()

        verify { passcodeRepository.lock() }
    }
}
