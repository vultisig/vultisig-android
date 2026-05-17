package com.vultisig.wallet.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.vultisig.wallet.data.sources.AppDataStore
import com.vultisig.wallet.data.sources.AppDataStoreImpl
import com.vultisig.wallet.data.utils.EncryptingSharedPreferences
import com.vultisig.wallet.data.utils.SECURE_PREFS_KEY_ALIAS
import com.vultisig.wallet.data.utils.SharedPrefsMasterKeyInitializer
import com.vultisig.wallet.data.utils.buildSecurePrefsKey
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.security.KeyStore
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.CompressorStreamProvider
import timber.log.Timber

/** Hilt module that wires application-scoped data-layer dependencies. */
@Module
@InstallIn(SingletonComponent::class)
internal interface MainDataModule {

    companion object {

        /** Provides the application-scoped [androidx.datastore.core.DataStore]. */
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context) =
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("app_pref") }
            )

        /** Provides the [IoDispatcher]-qualified [CoroutineDispatcher]. */
        @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        /** Provides the [MainDispatcher]-qualified [CoroutineDispatcher]. */
        @Provides
        @MainDispatcher
        fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

        /** Provides the [DefaultDispatcher]-qualified [CoroutineDispatcher]. */
        @Provides
        @DefaultDispatcher
        fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

        /** Provides the singleton [CompressorStreamProvider] for compression/decompression. */
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
                // Use the prewarm result if it completed before Hilt arrived; otherwise fall back
                // to a fresh keystore lookup.
                val prewarm = SharedPrefsMasterKeyInitializer.prewarmResult
                val key =
                    if (prewarm.isCompleted) prewarm.getCompleted() ?: buildSecurePrefsKey()
                    else buildSecurePrefsKey()
                val rawPrefs = context.getSharedPreferences(SECURE_PREFS_FILE, Context.MODE_PRIVATE)
                val prefs = EncryptingSharedPreferences(rawPrefs, key)
                // Eagerly probe the key: KeyPermanentlyInvalidatedException (a
                // GeneralSecurityException) surfaces here rather than silently returning defaults
                // on every subsequent read.
                prefs.selfTest()
                // Run migration on IO to avoid blocking the calling thread (often main). The
                // migration is idempotent and reaps its own legacy artifacts (master-key alias and
                // sentinel file) once it confirms there is nothing left to migrate.
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    migrateFromEncryptedSharedPrefs(context, prefs)
                }
                return prefs
            }

            fun recoverAndRetry(cause: KeyPermanentlyInvalidatedException): SharedPreferences {
                Timber.w(
                    cause,
                    "KeyPermanentlyInvalidatedException detected; performing destructive recovery",
                )
                // File first so a partial recovery (alias delete fails below) leaves no
                // ciphertext readable by the next launch; that next launch will re-enter recovery
                // and retry the alias delete.
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
                // Clear the migration sentinel so that if the legacy file is still present it can
                // be re-imported into the freshly generated key on the next create() call. The
                // sentinel lives in a separate prefs file (see LegacyEncryptedPrefsMigration.kt)
                // so it is not wiped by the secure-prefs deletion above.
                val sentinelCleared =
                    runCatching {
                            context
                                .getSharedPreferences("migration_state_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .remove("__migrated_from_encrypted_prefs")
                                .commit()
                        }
                        .onFailure {
                            Timber.w(it, "Failed to clear migration marker during recovery")
                        }
                        .getOrDefault(false)
                if (!sentinelCleared) {
                    // commit() returning false (or throwing) means the marker may still be set; log
                    // loudly so that if the legacy file is still present we have a trail explaining
                    // why migration did not re-run. create() still proceeds — we cannot leave Hilt
                    // without a SharedPreferences and the alias has already been rotated.
                    Timber.e("Migration marker not cleared; legacy data may not be re-imported")
                }
                return create()
            }

            // Issue #4401: only KeyPermanentlyInvalidatedException is destructive; transient
            // GeneralSecurityException / IOException must propagate so user data is not wiped.
            return try {
                create()
            } catch (e: KeyPermanentlyInvalidatedException) {
                recoverAndRetry(e)
            }
        }
    }

    /** Binds [AppDataStoreImpl] as the [AppDataStore] implementation. */
    @Singleton @Binds fun bindAppDataStore(impl: AppDataStoreImpl): AppDataStore
}

private const val SECURE_PREFS_FILE = "token_secure_prefs"

/** Qualifier for the IO [CoroutineDispatcher]. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

/** Qualifier for the main-thread [CoroutineDispatcher]. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainDispatcher

/** Qualifier for the default [CoroutineDispatcher]. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher
