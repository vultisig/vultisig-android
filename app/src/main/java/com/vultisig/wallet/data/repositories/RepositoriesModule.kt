package com.vultisig.wallet.data.repositories

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface RepositoriesModule {

    @Binds
    @Singleton
    fun bindChainAccountsRepository(
        impl: AccountsRepositoryImpl,
    ): AccountsRepository

    @Singleton
    @Binds
    fun bindOnBoardRepository(
        impl: OnBoardRepositoryImpl,
    ): OnBoardRepository

    @Binds
    @Singleton
    fun bindSwapTransactionRepository(
        impl: SwapTransactionRepositoryImpl
    ): SwapTransactionRepository
}