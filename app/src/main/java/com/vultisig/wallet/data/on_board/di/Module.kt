package com.vultisig.wallet.data.on_board.di

import android.content.Context
import com.vultisig.wallet.data.common.data_store.AppDataStore
import com.vultisig.wallet.data.on_board.repository.DataStoreRepositoryImpl
import com.vultisig.wallet.on_board.OnBoardRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class Module {

    @Singleton
    @Binds
    abstract fun bindOnBoardRepository(
        onBindRepositoryImpl: DataStoreRepositoryImpl,
    ): OnBoardRepository

}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideAppDataStore(@ApplicationContext context: Context): AppDataStore {
        return AppDataStore(context)
    }
}