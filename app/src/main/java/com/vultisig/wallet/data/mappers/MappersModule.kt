package com.vultisig.wallet.data.mappers

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface MappersModule {

    @Binds
    fun bindChainAndTokensToAddressMapper(
        impl: ChainAndTokensToAddressMapperImpl
    ): ChainAndTokensToAddressMapper

    @Binds
    fun bindVaultIOSToAndroidMapper(
        impl: VaultIOSToAndroidMapperImpl
    ): VaultIOSToAndroidMapper

    @Binds
    fun bindVaultAndroidToIOSMapper(
        impl: VaultAndroidToIOSMapperImpl
    ): VaultAndroidToIOSMapper

}