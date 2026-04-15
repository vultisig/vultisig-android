package com.vultisig.wallet.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vultisig.wallet.data.sources.AppDataStore
import com.vultisig.wallet.data.sources.AppDataStoreImpl
import com.vultisig.wallet.data.utils.buildMasterKey
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

        @Singleton
        @Provides
        fun provideEncryptedSharedPrefs(@ApplicationContext context: Context): SharedPreferences {
            fun create(): SharedPreferences =
                EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_FILE,
                    buildMasterKey(context),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )

            fun recoverAndRetry(cause: Exception): SharedPreferences {
                Timber.e(cause, "EncryptedSharedPrefs init failed, attempting recovery")
                val prefsFile =
                    File(context.filesDir.parent, "shared_prefs/$ENCRYPTED_PREFS_FILE.xml")
                if (prefsFile.exists() && !prefsFile.delete()) {
                    Timber.w(
                        "Failed to delete corrupted encrypted prefs file at %s",
                        prefsFile.absolutePath,
                    )
                }
                runCatching {
                        val keyStore = KeyStore.getInstance("AndroidKeyStore")
                        keyStore.load(null)
                        keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    }
                    .onFailure { Timber.e(it, "Failed to delete master key entry during recovery") }
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

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher
