package com.vultisig.wallet.app.passcode

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.vultisig.wallet.data.passcode.AutoLockRepository
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.data.passcode.PasscodeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Re-locks the app once it has been out of the foreground for longer than the user's chosen
 * timeout.
 *
 * Two mechanisms, because neither is sufficient alone. A timer started on background covers the
 * common case and means the data key is not held in memory a moment longer than the user asked for.
 * But a backgrounded process can be frozen or killed before that timer fires, so the elapsed time
 * is also re-checked on return. Whichever notices first wins.
 *
 * [SystemClock.elapsedRealtime] is the clock for that check: it keeps counting through deep sleep,
 * unlike uptime, and cannot be wound backwards by changing the system time.
 */
@Singleton
internal class PasscodeAutoLock(
    private val passcodeRepository: PasscodeRepository,
    private val autoLockRepository: AutoLockRepository,
    private val elapsedRealtimeMillis: () -> Long,
    parentScope: CoroutineScope?,
) : DefaultLifecycleObserver {

    @Inject
    constructor(
        passcodeRepository: PasscodeRepository,
        autoLockRepository: AutoLockRepository,
    ) : this(passcodeRepository, autoLockRepository, SystemClock::elapsedRealtime, null)

    // Main.immediate so the lock lands in the same frame the app leaves the foreground, before
    // anything can screenshot the task for the recents list.
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Mirrors the persisted timeout so [onStop] can decide synchronously. Reading the flow at that
     * moment would suspend, and the app can be frozen before the read completes.
     */
    @Volatile private var timeout: AutoLockTimeout = AutoLockTimeout.Default

    private var backgroundedAtMillis: Long? = null
    private var pendingLock: Job? = null

    /** Starts mirroring the timeout and observing [lifecycle]. Safe to call once per process. */
    fun start(lifecycle: Lifecycle) {
        scope.launch { autoLockRepository.timeout.collect { timeout = it } }
        lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        val current = timeout
        if (current == AutoLockTimeout.Immediate) {
            Timber.d("Auto-locking immediately on background")
            passcodeRepository.lock()
            return
        }

        backgroundedAtMillis = elapsedRealtimeMillis()
        pendingLock?.cancel()
        pendingLock =
            scope.launch {
                delay(current.minutes * MILLIS_PER_MINUTE)
                Timber.d("Auto-locking after %d minute(s) in the background", current.minutes)
                passcodeRepository.lock()
            }
    }

    override fun onStart(owner: LifecycleOwner) {
        pendingLock?.cancel()
        pendingLock = null

        val backgroundedAt = backgroundedAtMillis ?: return
        backgroundedAtMillis = null

        val awayMillis = elapsedRealtimeMillis() - backgroundedAt
        if (awayMillis >= timeout.minutes * MILLIS_PER_MINUTE) {
            Timber.d("Auto-locking: away for %d ms", awayMillis)
            passcodeRepository.lock()
        }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
