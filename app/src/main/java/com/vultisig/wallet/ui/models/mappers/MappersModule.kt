package com.vultisig.wallet.ui.models.mappers

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface MappersModule {

    @Binds
    @Singleton
    fun bindChainAccountToChainAccountUiModelMapper(
        impl: ChainAccountToChainAccountUiModelMapperImpl
    ): ChainAccountToChainAccountUiModelMapper

    @Binds
    @Singleton
    fun bindFiatValueToStringMapper(
        impl: FiatValueToStringMapperImpl
    ): FiatValueToStringMapper

    @Binds
    @Singleton
    fun bindAccountToTokenBalanceUiModelMapper(
        impl: AccountToTokenBalanceUiModelMapperImpl
    ): AccountToTokenBalanceUiModelMapper

}