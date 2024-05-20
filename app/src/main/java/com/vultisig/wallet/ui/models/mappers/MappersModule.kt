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
    fun bindAddressToUiModelMapper(
        impl: AddressToUiModelMapperImpl,
    ): AddressToUiModelMapper

    @Binds
    @Singleton
    fun bindFiatValueToStringMapper(
        impl: FiatValueToStringMapperImpl,
    ): FiatValueToStringMapper

    @Binds
    @Singleton
    fun bindAccountToTokenBalanceUiModelMapper(
        impl: AccountToTokenBalanceUiModelMapperImpl,
    ): AccountToTokenBalanceUiModelMapper

    @Binds
    @Singleton
    fun bindTokenValueToStringWithUnitMapper(
        impl: TokenValueToStringWithUnitMapperImpl,
    ): TokenValueToStringWithUnitMapper

    @Binds
    @Singleton
    fun bindTokenValueToDecimalUiStringMapper(
        impl: TokenValueToDecimalUiStringMapperImpl,
    ): TokenValueToDecimalUiStringMapper

    @Binds
    @Singleton
    fun bindFiatValueToValueStringMapper(
        impl: FiatValueToValueStringMapperImpl,
    ): FiatValueToValueStringMapper

    @Binds
    @Singleton
    fun bindTransactionToUiModelMapper(
        impl: TransactionToUiModelMapperImpl,
    ): TransactionToUiModelMapper

}