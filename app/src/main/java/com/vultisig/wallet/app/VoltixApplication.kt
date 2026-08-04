package com.vultisig.wallet.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.data.utils.SharedPrefsMasterKeyInitializer
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

internal open class VsBaseApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        WalletCoreLoader
        SharedPrefsMasterKeyInitializer.prewarm()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        initializeRive(this)
    }

    // Coin logos (e.g. Sui on-chain metadata icons) are sometimes served as SVG, which the
    // platform ImageDecoder rejects with "Failed to create image decoder ... unimplemented".
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).components { add(SvgDecoder.Factory()) }.build()
}

@HiltAndroidApp internal class VultisigApplication : VsBaseApplication()
