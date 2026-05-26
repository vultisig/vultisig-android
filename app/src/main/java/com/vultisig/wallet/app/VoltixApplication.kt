package com.vultisig.wallet.app

import android.app.Application
import android.content.Context
import android.os.Build
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

/**
 * Returns true when the current device is a known-bad target for Rive's GL renderer.
 *
 * The Rive native library aborts during GL shader compile on Tecno CAMON 30 (MediaTek MT6789, Mali
 * GPU on Android 15) — see issue #4656. Skipping `Rive.init` on matched devices leaves
 * [isRiveInitialized] `false` so composables fall back to a
 * [androidx.compose.foundation.layout.Spacer].
 */
@VisibleForTesting
internal fun isRiveUnsupportedDevice(
    manufacturer: String = Build.MANUFACTURER.orEmpty(),
    model: String = Build.MODEL.orEmpty(),
    socModel: String? = readSocModel(),
): Boolean {
    val matchesTecnoCamon30 =
        manufacturer.equals("TECNO", ignoreCase = true) &&
            model.contains("CAMON 30", ignoreCase = true)
    val matchesKnownBadSoc = socModel.equals("MT6789", ignoreCase = true)
    return matchesTecnoCamon30 || matchesKnownBadSoc
}

private fun readSocModel(): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null

internal fun initializeRive(context: Context) {
    if (isRiveUnsupportedDevice()) {
        Timber.w(
            "Skipping Rive init on known-bad GPU family (%s %s)",
            Build.MANUFACTURER,
            Build.MODEL,
        )
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
        SharedPrefsMasterKeyInitializer.prewarm()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        initializeRive(this)
    }
}

@HiltAndroidApp internal class VultisigApplication : VsBaseApplication()
