package com.vultisig.wallet.data.repositories

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataRepositoriesModule {

    @Binds
    @Singleton
    fun bindChainAccountAddressRepository(
        impl: ChainAccountAddressRepositoryImpl,
    ): ChainAccountAddressRepository

    @Binds
    @Singleton
    fun bindTokenRepository(
        impl: TokenRepositoryImpl,
    ): TokenRepository

    @Binds
    @Singleton
    fun bindVaultRepository(
        impl: VaultRepositoryImpl
    ): VaultRepository

    @Binds
    @Singleton
    fun bindVultiSignerRepository(
        impl: VultiSignerRepositoryImpl
    ): VultiSignerRepository

    @Binds
    @Singleton
    fun bindVaultDataStoreRepository(
        impl: VaultDataStoreRepositoryImpl
    ): VaultDataStoreRepository

    @Binds
    @Singleton
    fun bindTransactionRepository(
        impl: TransactionRepositoryImpl,
    ): TransactionRepository

    @Binds
    @Singleton
    fun bindAppCurrencyRepository(
        impl: AppCurrencyRepositoryImpl,
    ): AppCurrencyRepository

    @Binds
    @Singleton
    fun bindTokenPriceRepository(
        impl: TokenPriceRepositoryImpl,
    ): TokenPriceRepository

    @Binds
    @Singleton
    fun bindAppLocaleRepository(
        impl: AppLocaleRepositoryImpl,
    ): AppLocaleRepository
}