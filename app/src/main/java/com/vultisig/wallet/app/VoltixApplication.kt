package com.vultisig.wallet.app

import android.app.Application
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.data.utils.SharedPrefsMasterKeyInitializer
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

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
