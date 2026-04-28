package com.vultisig.wallet.data.utils

import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.SecretKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
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

    private val _prewarmResult: CompletableDeferred<SecretKey?> = CompletableDeferred()

    /**
     * [Deferred] that resolves to the pre-warmed [SecretKey], or null if the prewarm failed.
     * Callers can check [Deferred.isCompleted] and call [Deferred.getCompleted] to avoid a blocking
     * keystore round-trip when the key is already cached.
     */
    val prewarmResult: Deferred<SecretKey?>
        get() = _prewarmResult

    /** Fires a background key pre-warm; safe to call from Application.onCreate. */
    fun prewarm() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("SecurePrefsPrewarm"))
            .launch {
                try {
                    _prewarmResult.complete(buildSecurePrefsKey())
                } catch (e: GeneralSecurityException) {
                    Timber.w(e, "Secure prefs key prewarm failed; deferring to sync path")
                    _prewarmResult.complete(null)
                } catch (e: IOException) {
                    Timber.w(e, "Secure prefs key prewarm failed; deferring to sync path")
                    _prewarmResult.complete(null)
                }
            }
    }
}
