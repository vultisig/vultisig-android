package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.mappers.utils.MapHexToPlainString
import com.vultisig.wallet.data.mappers.utils.MapHexToPlainStringImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataMappersModule {

    @Binds
    @Singleton
    fun bindMapHexToPlainString(
        impl: MapHexToPlainStringImpl
    ): MapHexToPlainString

    @Binds
    fun bindChainAndTokensToAddressMapper(
        impl: ChainAndTokensToAddressMapperImpl
    ): ChainAndTokensToAddressMapper

}