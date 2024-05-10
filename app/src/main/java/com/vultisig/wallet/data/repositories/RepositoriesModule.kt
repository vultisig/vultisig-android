package com.vultisig.wallet.data.repositories

import android.content.Context
import com.vultisig.wallet.data.on_board.db.VaultDB
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface RepositoriesModule {

    @Binds
    @Singleton
    fun bindAppCurrencyRepository(
        impl: AppCurrencyRepositoryImpl,
    ): AppCurrencyRepository

    @Binds
    @Singleton
    fun bindChainAccountAddressRepository(
        impl: ChainAccountAddressRepositoryImpl
    ): ChainAccountAddressRepository

    @Binds
    @Singleton
    fun bindTokenPriceRepository(
        impl: TokenPriceRepositoryImpl,
    ): TokenPriceRepository

    @Binds
    @Singleton
    fun bindChainAccountsRepository(
        impl: AccountsRepositoryImpl,
    ): AccountsRepository

    @Binds
    @Singleton
    fun bindBalanceRepositoryImpl(
        impl: BalanceRepositoryImpl,
    ): BalanceRepository

    companion object {

        @Provides
        @Singleton
        fun provideVaultDb(
            @ApplicationContext context: Context,
        ) = VaultDB(context)

    }

}