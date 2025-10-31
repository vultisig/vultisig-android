package com.vultisig.wallet.app

import android.app.Application
import app.rive.runtime.kotlin.core.Rive
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.data.utils.SharedPrefsMasterKeyInitializer
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

internal open class VsBaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WalletCoreLoader
        SharedPrefsMasterKeyInitializer

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Rive.init(this)
    }
}

@HiltAndroidApp
internal class VultisigApplication : VsBaseApplication()