package com.vultisig.wallet.data.mappers

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    fun bindReshareMessageFromProtoMapper(
        impl: ReshareMessageFromProtoMapperImpl
    ): ReshareMessageFromProtoMapper

    @Binds
    @Singleton
    fun bindReshareMessageToProtoMapper(
        impl: ReshareMessageToProtoMapperImpl
    ): ReshareMessageToProtoMapper

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
    fun bindMapVaultToProto(
        impl: MapVaultToProtoImpl
    ): MapVaultToProto

}