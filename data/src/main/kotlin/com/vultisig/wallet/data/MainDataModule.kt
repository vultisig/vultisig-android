package com.vultisig.wallet.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vultisig.wallet.data.sources.AppDataStore
import com.vultisig.wallet.data.sources.AppDataStoreImpl
import com.vultisig.wallet.data.utils.EncryptingSharedPreferences
import com.vultisig.wallet.data.utils.SECURE_PREFS_KEY_ALIAS
import com.vultisig.wallet.data.utils.buildSecurePrefsKey
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.CompressorStreamProvider
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
internal interface MainDataModule {

    companion object {

        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context) =
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("app_pref") }
            )

        @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @MainDispatcher
        fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

        @Provides
        @DefaultDispatcher
        fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides
        @Singleton
        fun provideCompressorStreamProvider(): CompressorStreamProvider = CompressorStreamFactory()

        /**
         * Provides the singleton [SharedPreferences] backed by AndroidKeyStore AES-256-GCM
         * encryption.
         */
        @Singleton
        @Provides
        fun provideEncryptedSharedPrefs(@ApplicationContext context: Context): SharedPreferences {
            fun create(): SharedPreferences {
                val key = buildSecurePrefsKey()
                val rawPrefs = context.getSharedPreferences(SECURE_PREFS_FILE, Context.MODE_PRIVATE)
                val prefs = EncryptingSharedPreferences(rawPrefs, key)
                migrateFromEncryptedSharedPrefs(context, prefs)
                return prefs
            }

            fun recoverAndRetry(cause: Exception): SharedPreferences {
                Timber.e(cause, "SecureSharedPrefs init failed, attempting recovery")
                val prefsFile = File(context.filesDir.parent, "shared_prefs/$SECURE_PREFS_FILE.xml")
                if (prefsFile.exists() && !prefsFile.delete()) {
                    Timber.w(
                        "Failed to delete corrupted secure prefs file at %s",
                        prefsFile.absolutePath,
                    )
                }
                runCatching {
                        val keyStore = KeyStore.getInstance("AndroidKeyStore")
                        keyStore.load(null)
                        keyStore.deleteEntry(SECURE_PREFS_KEY_ALIAS)
                    }
                    .onFailure { Timber.e(it, "Failed to delete secure prefs key during recovery") }
                return create()
            }

            return try {
                create()
            } catch (e: GeneralSecurityException) {
                recoverAndRetry(e)
            } catch (e: IOException) {
                recoverAndRetry(e)
            }
        }
    }

    @Singleton @Binds fun bindAppDataStore(impl: AppDataStoreImpl): AppDataStore
}

private const val ENCRYPTED_PREFS_FILE = "token_encrypted_prefs"
private const val SECURE_PREFS_FILE = "token_secure_prefs"

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

/**
 * One-time migration: copies all entries from the legacy [EncryptedSharedPreferences] file to
 * [newPrefs], then deletes the legacy file. If the legacy file cannot be opened (e.g. the
 * AndroidKeyStore entry is corrupted), the legacy file is discarded rather than blocking startup.
 * The migration is idempotent — it is a no-op once the legacy file no longer exists.
 */
@Suppress("DEPRECATION")
private fun migrateFromEncryptedSharedPrefs(context: Context, newPrefs: SharedPreferences) {
    val legacyFile = File(context.filesDir.parent, "shared_prefs/$ENCRYPTED_PREFS_FILE.xml")
    if (!legacyFile.exists()) return

    val legacyPrefs =
        try {
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_FILE,
                MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .setRequestStrongBoxBacked(false)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Timber.e(e, "Cannot open legacy encrypted prefs for migration; discarding legacy data")
            legacyFile.delete()
            return
        }

    val editor = newPrefs.edit()
    for ((key, value) in legacyPrefs.all) {
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
        }
    }
    if (editor.commit()) {
        legacyFile.delete()
        Timber.i("Migrated encrypted prefs to secure format")
    } else {
        Timber.w("Failed to commit migrated prefs; legacy file retained for next attempt")
    }
}
