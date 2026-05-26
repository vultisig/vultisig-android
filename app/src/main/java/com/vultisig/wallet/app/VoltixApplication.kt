package com.vultisig.wallet.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import app.rive.runtime.kotlin.core.Rive
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.data.utils.SharedPrefsMasterKeyInitializer
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

internal var isRiveInitialized: Boolean = false
    private set

@VisibleForTesting
internal fun resetRiveInitialized() {
    isRiveInitialized = false
}

private const val RIVE_GUARD_PREFS = "rive_init_guard"
private const val KEY_RIVE_INIT_IN_FLIGHT = "rive_init_in_flight"

/**
 * Returns the [SharedPreferences] backing the Rive crash-loop guard.
 *
 * Kept as a plain (unencrypted) prefs file — the only stored value is a boolean "init attempted"
 * marker — and resolved synchronously so [initializeRive] can read/write before invoking the native
 * Rive entry point.
 */
@VisibleForTesting
internal fun riveGuardPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences(RIVE_GUARD_PREFS, Context.MODE_PRIVATE)

/**
 * Initializes the Rive SDK behind a device-agnostic crash-loop guard.
 *
 * Mitigates the native `SIGABRT` in `librive-android.so` (see issue #4656) without keying on
 * manufacturer/model strings. A persistent flag is set before [Rive.init] and cleared only after it
 * returns; if the previous launch died inside the native call, the flag survives and the next
 * launch short-circuits — leaving [isRiveInitialized] `false` so composables fall back to a
 * [androidx.compose.foundation.layout.Spacer].
 */
internal fun initializeRive(context: Context) {
    val prefs = riveGuardPrefs(context)
    if (prefs.getBoolean(KEY_RIVE_INIT_IN_FLIGHT, false)) {
        Timber.w(
            "Skipping Rive init: previous launch did not complete Rive.init (likely native crash)"
        )
        return
    }
    // commit() (not apply()) so the marker is durable on disk before we cross the JNI boundary —
    // an async write would race a SIGABRT and lose the signal we rely on next launch.
    prefs.edit().putBoolean(KEY_RIVE_INIT_IN_FLIGHT, true).commit()
    try {
        Rive.init(context)
        isRiveInitialized = true
        prefs.edit().putBoolean(KEY_RIVE_INIT_IN_FLIGHT, false).commit()
    } catch (e: Throwable) {
        Timber.e(e, "Failed to initialize Rive SDK, animations will be disabled")
    }
}

internal open class VsBaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WalletCoreLoader
        SharedPrefsMasterKeyInitializer.prewarm()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        initializeRive(this)
    }
}

@HiltAndroidApp internal class VultisigApplication : VsBaseApplication()
