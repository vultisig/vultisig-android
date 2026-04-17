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
        WalletCoreLoader
        SharedPrefsMasterKeyInitializer.prewarm(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        initializeRive(this)
    }
}

@HiltAndroidApp internal class VultisigApplication : VsBaseApplication()
