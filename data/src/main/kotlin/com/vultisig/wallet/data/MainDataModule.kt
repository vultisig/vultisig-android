package com.vultisig.wallet.data

import android.content.Context
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
    }

}