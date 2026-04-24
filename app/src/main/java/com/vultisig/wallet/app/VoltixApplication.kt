package com.vultisig.wallet.app

import android.app.Application
import android.content.Context
import android.opengl.EGL14
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

internal fun isMaliGpu(): Boolean =
    try {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        EGL14.eglInitialize(display, IntArray(1), 0, IntArray(1), 0)
        val vendor = EGL14.eglQueryString(display, EGL14.EGL_VENDOR) ?: ""
        // Mali EGL implementations report EGL_VENDOR = "ARM"; some variants include "mali"
        val vendorLower = vendor.lowercase()
        "arm" in vendorLower || "mali" in vendorLower
    } catch (_: Throwable) {
        false
    }

internal fun initializeRive(context: Context) {
    if (isMaliGpu()) {
        Timber.w("Skipping Rive initialization on Mali GPU to prevent librive-android.so crash")
        return
    }
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
