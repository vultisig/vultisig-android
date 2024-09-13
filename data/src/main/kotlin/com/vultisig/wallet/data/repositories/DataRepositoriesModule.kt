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
}