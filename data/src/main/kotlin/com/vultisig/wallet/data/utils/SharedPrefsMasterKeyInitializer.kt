package com.vultisig.wallet.data.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Pre-warms the AndroidKeyStore secure-prefs key on a background thread so the first injection of
 * [android.content.SharedPreferences] from Hilt does not have to pay the keystore round-trip. Runs
 * fire-and-forget — any failure is logged and swallowed since the synchronous path in
 * [com.vultisig.wallet.data.MainDataModule.provideEncryptedSharedPrefs] will retry and recover.
 */
object SharedPrefsMasterKeyInitializer {

    /** Fires a background key pre-warm; safe to call from Application.onCreate. */
    @Suppress("UNUSED_PARAMETER")
    fun prewarm(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { buildSecurePrefsKey() }
                .onFailure {
                    Timber.w(it, "Secure prefs key prewarm failed; deferring to sync path")
                }
        }
    }
}
