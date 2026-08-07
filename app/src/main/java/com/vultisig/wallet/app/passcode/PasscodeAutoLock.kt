package com.vultisig.wallet.app.passcode

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.vultisig.wallet.data.passcode.AutoLockHold
import com.vultisig.wallet.data.passcode.AutoLockRepository
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.data.passcode.PasscodeRepository
import java.util.concurrent.atomic.AtomicBoolean
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
 *
 * This is not, and cannot be, what keeps the app off the recents thumbnail. `ProcessLifecycleOwner`
 * holds ON_STOP back by 700ms so that a configuration change does not read as leaving the app, so
 * even the [AutoLockTimeout.Immediate] lock lands well after the system has taken its snapshot.
 * `FLAG_SECURE` is what covers that surface, and the prevent-screenshots setting is what sets it.
 */
@Singleton
internal class PasscodeAutoLock(
    private val passcodeRepository: PasscodeRepository,
    private val autoLockRepository: AutoLockRepository,
    private val autoLockHold: AutoLockHold,
    private val elapsedRealtimeMillis: () -> Long,
    parentScope: CoroutineScope?,
) : DefaultLifecycleObserver {

    @Inject
    constructor(
        passcodeRepository: PasscodeRepository,
        autoLockRepository: AutoLockRepository,
        autoLockHold: AutoLockHold,
    ) : this(
        passcodeRepository,
        autoLockRepository,
        autoLockHold,
        SystemClock::elapsedRealtime,
        null,
    )

    // Main.immediate so the lock is dispatched as soon as the app leaves the foreground rather
    // than queued behind whatever else the main thread has pending.
    private val scope = parentScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Mirrors the persisted timeout so [onStop] can decide synchronously. Reading the flow at that
     * moment would suspend, and the app can be frozen before the read completes.
     */
    @Volatile private var timeout: AutoLockTimeout = AutoLockTimeout.Default

    private var backgroundedAtMillis: Long? = null
    private var pendingLock: Job? = null

    private val isStarted = AtomicBoolean(false)

    /**
     * Starts mirroring the timeout and observing [lifecycle]. Repeat calls are ignored: this is a
     * process singleton but the caller is `MainActivity.onCreate`, which runs again on every
     * configuration change, and each pass would otherwise leak another timeout collector.
     */
    fun start(lifecycle: Lifecycle) {
        if (!isStarted.compareAndSet(false, true)) return
        scope.launch { autoLockRepository.timeout.collect { timeout = it } }
        lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        // Stamped before the immediate branch so the elapsed-time check on return stays meaningful
        // whatever the timeout was when the app left, including if it changes while away.
        backgroundedAtMillis = elapsedRealtimeMillis()
        pendingLock?.cancel()

        val current = timeout
        if (current == AutoLockTimeout.Never) {
            pendingLock = null
            return
        }

        pendingLock =
            scope.launch {
                delay(current.minutes * MILLIS_PER_MINUTE)
                lockWhenNothingHoldsIt(
                    "Auto-locking after ${current.minutes} minute(s) in the background"
                )
            }
    }

    override fun onStart(owner: LifecycleOwner) {
        pendingLock?.cancel()
        pendingLock = null

        val backgroundedAt = backgroundedAtMillis ?: return
        backgroundedAtMillis = null

        val current = timeout
        if (current == AutoLockTimeout.Never) return

        val awayMillis = elapsedRealtimeMillis() - backgroundedAt
        if (awayMillis >= current.minutes * MILLIS_PER_MINUTE) {
            pendingLock =
                scope.launch { lockWhenNothingHoldsIt("Auto-locking: away for $awayMillis ms") }
        }
    }

    /**
     * Locks, once nothing is holding it off.
     *
     * The wait is not a grace period — the timeout has already elapsed. It exists because a keygen
     * ceremony in flight has not yet written its keyshare, and locking first destroys that share
     * outright. See [AutoLockHold].
     */
    private suspend fun lockWhenNothingHoldsIt(reason: String) {
        if (autoLockHold.holds.value > 0) {
            Timber.d("Auto-lock deferred: an operation is in flight")
            autoLockHold.awaitRelease()
        }
        Timber.d(reason)
        passcodeRepository.lock()
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
