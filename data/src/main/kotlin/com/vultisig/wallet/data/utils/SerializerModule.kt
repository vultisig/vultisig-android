package com.vultisig.wallet.data.utils

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
internal interface SerializerModule {

    @Binds
    @Singleton
    fun bindSplTokenResponseJsonSerializer(
        impl: SplTokenResponseJsonSerializerImpl,
    ): SplTokenResponseJsonSerializer

    @Binds
    @Singleton
    fun bindCosmosThorChainResponseSerializer(
        impl: CosmosThorChainResponseSerializerImpl,
    ): CosmosThorChainResponseSerializer
}