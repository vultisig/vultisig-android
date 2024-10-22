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
    fun bindVaultIOSToAndroidMapper(
        impl: VaultFromOldJsonMapperImpl
    ): VaultFromOldJsonMapper

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
    fun bindMapVaultToProto(
        impl: MapVaultToProtoImpl
    ): MapVaultToProto

}