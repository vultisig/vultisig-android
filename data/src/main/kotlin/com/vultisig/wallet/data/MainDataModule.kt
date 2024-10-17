package com.vultisig.wallet.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.vultisig.wallet.data.sources.AppDataStore
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
        fun provideAppDataStore(
            @ApplicationContext context: Context
        ): AppDataStore = AppDataStore(context)

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

}