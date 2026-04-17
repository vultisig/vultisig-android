package com.vultisig.wallet.app

import android.app.Application
import app.rive.runtime.kotlin.core.Rive
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.data.utils.SharedPrefsMasterKeyInitializer
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

internal var isRiveInitialized: Boolean = false
    private set

internal open class VsBaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WalletCoreLoader
        SharedPrefsMasterKeyInitializer.prewarm(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        try {
            Rive.init(this)
            isRiveInitialized = true
        } catch (e: Throwable) {
            Timber.e(e, "Failed to initialize Rive SDK, animations will be disabled")
        }
    }
}

@HiltAndroidApp internal class VultisigApplication : VsBaseApplication()
