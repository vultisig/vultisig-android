package com.vultisig.wallet.data.usecases

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface UseCasesModule {

    @Binds
    @Singleton
    fun bindConvertTokenValueToFiatUseCase(
        impl: ConvertTokenValueToFiatUseCaseImpl
    ): ConvertTokenValueToFiatUseCase

    @Binds
    @Singleton
    fun bindConvertTokenAndValueToTokenValueUseCase(
        impl: ConvertTokenAndValueToTokenValueUseCaseImpl
    ): ConvertTokenAndValueToTokenValueUseCase

    @Binds
    @Singleton
    fun bindSaveVaultUseCase(
        impl: SaveVaultUseCaseImpl
    ): SaveVaultUseCase

}