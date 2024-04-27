package com.voltix.wallet.data.on_board.di

import com.voltix.wallet.data.on_board.repository.DataStoreRepositoryImpl
import com.voltix.wallet.on_board.OnBoardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class Module {

    @Singleton
    @Binds
    abstract fun bindOnBoardRepository(
        onBindRepositoryImpl: DataStoreRepositoryImpl
    ): OnBoardRepository

}