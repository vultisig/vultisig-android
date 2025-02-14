package com.vultisig.wallet.app

import android.app.Application
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive
import com.vultisig.wallet.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
internal class VultisigApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WalletCoreLoader

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Rive.init(this)
    }
}