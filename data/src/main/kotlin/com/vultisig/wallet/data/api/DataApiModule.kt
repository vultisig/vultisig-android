package com.vultisig.wallet.data.api

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataApiModule {

    @Binds
    @Singleton
    fun bindOneInchApi(
        impl: OneInchApiImpl
    ): OneInchApi

    @Binds
    @Singleton
    fun bindVultiSignerApi(
        impl: VultiSignerApiImpl
    ): VultiSignerApi

    @Binds
    @Singleton
    fun bindEvmApi(
        impl: EvmApiFactoryImp,
    ): EvmApiFactory

    @Binds
    @Singleton
    fun bindPolkadotApi(
        impl: PolkadotApiImp
    ): PolkadotApi

    @Binds
    fun bindKeygenApi(
        impl: KeyApiImp
    ): KeyApi
}