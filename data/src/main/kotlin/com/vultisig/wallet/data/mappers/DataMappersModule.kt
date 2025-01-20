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

    @Binds
    @Singleton
    fun bindKeygenMessageFromProtoMapper(
        impl: KeygenMessageFromProtoMapperImpl
    ): KeygenMessageFromProtoMapper

    @Binds
    @Singleton
    fun bindKeygenMessageToProtoMapper(
        impl: KeygenMessageToProtoMapperImpl
    ): KeygenMessageToProtoMapper
    @Binds
    @Singleton
    fun bindKeysignMessageFromProtoMapper(
        impl: KeysignMessageFromProtoMapperImpl
    ): KeysignMessageFromProtoMapper

    @Binds
    @Singleton
    fun bindKeysignPayloadProtoMapper(
        impl: KeysignPayloadProtoMapperImpl
    ): KeysignPayloadProtoMapper

    @Binds
    @Singleton
    fun bindSplTokenJsonFromSplTokenInfoMapper(
        impl: SplTokenJsonFromSplTokenInfoImpl
    ): SplTokenJsonFromSplTokenInfoMapper

    @Binds
    @Singleton
    fun bindSplResponseAccountJsonMapper(
        impl: SplResponseAccountJsonMapperImpl
    ): SplResponseAccountJsonMapper

    @Binds
    @Singleton
    fun bindPayloadToProtoMapper(
        impl: PayloadToProtoMapperImpl
    ): PayloadToProtoMapper
}


