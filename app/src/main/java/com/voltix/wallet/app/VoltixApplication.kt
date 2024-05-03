package com.voltix.wallet.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoltixApplication : Application(){
    override fun onCreate() {
        super.onCreate()
        WalletCoreLoader
    }
}