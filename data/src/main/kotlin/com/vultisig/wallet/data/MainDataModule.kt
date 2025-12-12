package com.vultisig.wallet.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.vultisig.wallet.data.sources.AppDataStore
import com.vultisig.wallet.data.sources.AppDataStoreImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.CompressorStreamProvider
import javax.inject.Singleton

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

        @Provides
        @Singleton
        fun provideCompressorStreamProvider(): CompressorStreamProvider =
            CompressorStreamFactory()

        @Singleton
        @Provides
        fun provideEncryptedSharedPrefs(@ApplicationContext context: Context): SharedPreferences {
            val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

            return EncryptedSharedPreferences.create(
                "token_encrypted_prefs",
                masterKey,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    @Singleton
    @Binds
    fun bindAppDataStore(
        impl: AppDataStoreImpl,
    ): AppDataStore

}