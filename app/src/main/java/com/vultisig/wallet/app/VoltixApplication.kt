package com.vultisig.wallet.app

import android.app.Application
import android.content.Context
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

internal fun initializeRive(context: Context) {
    try {
        Rive.init(context)
        isRiveInitialized = true
    } catch (e: Throwable) {
        Timber.e(e, "Failed to initialize Rive SDK, animations will be disabled")
    }
}

internal open class VsBaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // [boot4360] plant Timber FIRST so all subsequent boot-trace logs are captured.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        val t0 = System.nanoTime()
        Timber.i("[boot4360] App.onCreate start, thread=%s", Thread.currentThread().name)
        WalletCoreLoader
        Timber.i(
            "[boot4360] App.onCreate WalletCoreLoader done, elapsed=%dms",
            (System.nanoTime() - t0) / 1_000_000,
        )
        SharedPrefsMasterKeyInitializer.prewarm()
        Timber.i(
            "[boot4360] App.onCreate prewarm dispatched, elapsed=%dms",
            (System.nanoTime() - t0) / 1_000_000,
        )

        initializeRive(this)
        Timber.i("[boot4360] App.onCreate end, elapsed=%dms", (System.nanoTime() - t0) / 1_000_000)
    }
}

@HiltAndroidApp internal class VultisigApplication : VsBaseApplication()
