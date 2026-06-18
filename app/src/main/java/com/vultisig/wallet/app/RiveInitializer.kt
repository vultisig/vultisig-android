package com.vultisig.wallet.app

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import app.rive.runtime.kotlin.core.Rive
import com.vultisig.wallet.BuildConfig
import timber.log.Timber

internal var isRiveInitialized: Boolean = false
    private set

@VisibleForTesting
internal fun resetRiveInitialized() {
    isRiveInitialized = false
}

private const val RIVE_GUARD_PREFS = "rive_init_guard"
private const val KEY_RIVE_INIT_IN_FLIGHT_VERSION = "rive_init_in_flight_version"
internal const val NO_IN_FLIGHT_VERSION = -1

/**
 * Returns the [SharedPreferences] backing the Rive crash-loop guard.
 *
 * Kept as a plain (unencrypted) prefs file — the only stored value is an int "init attempted"
 * marker keyed by app version — and resolved synchronously so [initializeRive] can read/write
 * before invoking the native Rive entry point.
 */
@VisibleForTesting
internal fun riveGuardPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences(RIVE_GUARD_PREFS, Context.MODE_PRIVATE)

/**
 * Initializes the Rive SDK behind a device-agnostic crash-loop guard.
 *
 * Mitigates the native `SIGABRT` in `librive-android.so` (see issue #4656) without keying on
 * manufacturer/model strings. Before [Rive.init] we store the current [BuildConfig.VERSION_CODE];
 * the `finally` block clears it on any normal return (success or catchable throw). Only an
 * uncatchable process death inside the native call leaves the stored version set, causing the next
 * launch at the *same* version to short-circuit and leave [isRiveInitialized] `false` so
 * composables fall back to a [androidx.compose.foundation.layout.Spacer]. A later install that
 * bumps `versionCode` (e.g. ships a fixed Rive lib) will not match the stored version, so Rive is
 * re-attempted automatically without a manual reset.
 */
internal fun initializeRive(context: Context) {
    val prefs = riveGuardPrefs(context)
    val storedVersion = prefs.getInt(KEY_RIVE_INIT_IN_FLIGHT_VERSION, NO_IN_FLIGHT_VERSION)
    if (storedVersion == BuildConfig.VERSION_CODE) {
        Timber.w(
            "Skipping Rive init: previous launch at version %d did not complete Rive.init " +
                "(likely native crash)",
            BuildConfig.VERSION_CODE,
        )
        return
    }
    // commit() (not apply()) so the marker is durable on disk before we cross the JNI boundary —
    // an async write would race a SIGABRT and lose the signal we rely on next launch. If the
    // durable write fails (full disk, I/O error, thread interruption) we skip Rive.init entirely:
    // the guard could not detect a native crash next launch, so running unguarded risks a crash
    // loop. We leave isRiveInitialized false and composables fall back gracefully.
    val committed =
        prefs.edit().putInt(KEY_RIVE_INIT_IN_FLIGHT_VERSION, BuildConfig.VERSION_CODE).commit()
    if (!committed) {
        Timber.w("Skipping Rive init: failed to durably persist the in-flight crash-loop marker")
        return
    }
    try {
        Rive.init(context)
        isRiveInitialized = true
    } catch (e: LinkageError) {
        // Native library load/link failure (e.g. UnsatisfiedLinkError) — Rive can't run on this
        // device. Catchable, so the finally clears the marker and Rive is retried next launch.
        Timber.e(e, "Failed to load Rive SDK native components, animations will be disabled")
    } catch (e: RuntimeException) {
        Timber.e(e, "Failed to initialize Rive SDK, animations will be disabled")
    } finally {
        // apply() is fine on the clear path — we only need durability for the *set* (which races
        // SIGABRT). On normal return the next read happens after this process writes, so async is
        // safe and keeps the cold-start path off a second blocking disk write.
        prefs.edit().putInt(KEY_RIVE_INIT_IN_FLIGHT_VERSION, NO_IN_FLIGHT_VERSION).apply()
    }
}
