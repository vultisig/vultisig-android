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
    fun bindBigDecimalSerializer(
        impl: BigDecimalSerializerImpl,
    ): BigDecimalSerializer

    @Binds
    @Singleton
    fun bindBigIntegerSerializer(
        impl: BigIntegerSerializerImpl,
    ): BigIntegerSerializer

    @Binds
    @Singleton
    fun bindTHORChainSwapQuoteResponseJsonSerializer(
        impl: ThorChainSwapQuoteResponseJsonSerializerImpl,
    ): ThorChainSwapQuoteResponseJsonSerializer

    @Binds
    @Singleton
    fun bindKeysignResponseSerializer(
        impl: KeysignResponseSerializerImpl,
    ): KeysignResponseSerializer

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

    @Binds
    @Singleton
    fun bindLiFiSwapQuoteResponseSerializer(
        impl: LiFiSwapQuoteResponseSerializerImpl,
    ): LiFiSwapQuoteResponseSerializer

    @Binds
    @Singleton
    fun bindUTXoStatusResponseSerializer(
        impl: UTXOStatusResponseSerializerImpl,
    ): UTXOStatusResponseSerializer


    @Binds
    @Singleton
    fun bindOneInchSwapQuoteResponseJsonSerializer(
        impl: OneInchSwapQuoteResponseJsonSerializerImpl,
    ): OneInchSwapQuoteResponseJsonSerializer

    @Binds
    @Singleton
    fun bindKyberSwapQuoteResponseJsonSerializer(
        impl: KyberSwapQuoteResponseJsonSerializerImpl,
    ): KyberSwapQuoteResponseJsonSerializer
}