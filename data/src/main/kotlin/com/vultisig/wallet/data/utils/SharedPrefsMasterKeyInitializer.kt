package com.vultisig.wallet.data.utils

import android.content.Context
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Pre-warms the AndroidKeyStore master key on a background thread so the first injection of
 * [android.content.SharedPreferences] from Hilt does not have to pay the keystore round-trip. Runs
 * fire-and-forget — any failure is logged and swallowed since the synchronous path in
 * [com.vultisig.wallet.data.MainDataModule.provideEncryptedSharedPrefs] will retry and recover.
 */
object SharedPrefsMasterKeyInitializer {

    fun prewarm(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { buildMasterKey(appContext) }
                .onFailure { Timber.w(it, "Master key prewarm failed; deferring to sync path") }
        }
    }
}

/**
 * Builds the AES-256-GCM master key for [androidx.security.crypto.EncryptedSharedPreferences].
 *
 * All encrypted-prefs call sites should use this factory instead of instantiating
 * [MasterKey.Builder] directly so the key scheme and StrongBox policy stay in one place.
 *
 * StrongBox is explicitly disabled: the StrongBox path is known to stall the keystore daemon on
 * some Pixel and Samsung devices, and the standard TEE-backed key is sufficient for the encrypted
 * prefs use-case.
 *
 * Blocking — do not call on the main thread.
 */
internal fun buildMasterKey(context: Context): MasterKey =
    MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(false)
        .build()
