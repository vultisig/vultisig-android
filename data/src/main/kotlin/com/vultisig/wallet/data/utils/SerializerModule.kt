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
    fun bindBigDecimalSerializer(
        impl: BigDecimalSerializerImpl,
    ): BigDecimalSerializer

    @Binds
    fun bindBigIntegerSerializer(
        impl: BigIntegerSerializerImpl,
    ): BigIntegerSerializer

    @Binds
    fun bindTHORChainSwapQuoteResponseJsonSerializer(
        impl: ThorChainSwapQuoteResponseJsonSerializerImpl,
    ): ThorChainSwapQuoteResponseJsonSerializer

    @Binds
    fun bindKeysignResponseSerializer(
        impl: KeysignResponseSerializerImpl,
    ): KeysignResponseSerializer

    @Binds
    fun bindSplTokenResponseJsonSerializer(
        impl: SplTokenResponseJsonSerializerImpl,
    ): SplTokenResponseJsonSerializer

    @Binds
    fun bindCosmosThorChainResponseSerializer(
        impl: CosmosThorChainResponseSerializerImpl,
    ): CosmosThorChainResponseSerializer

    @Binds
    @Singleton
    fun bindOneInchSwapQuoteResponseJsonSerializer(
        impl: OneInchSwapQuoteResponseJsonSerializerImpl,
    ): OneInchSwapQuoteResponseJsonSerializer
}