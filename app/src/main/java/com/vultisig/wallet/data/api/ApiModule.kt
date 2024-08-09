package com.vultisig.wallet.data.api

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ApiModule {

    @Binds
    @Singleton
    fun bindCoinGeckoApi(
        impl: CoinGeckoApiImpl,
    ): CoinGeckoApi

    @Binds
    @Singleton
    fun bindThorChainApi(
        impl: ThorChainApiImpl,
    ): ThorChainApi

    @Binds
    @Singleton
    fun bindMayaChainApi(
        impl: MayaChainApiImp,
    ): MayaChainApi

    @Binds
    @Singleton
    fun bindBlockChairApi(
        impl: BlockChairApiImp,
    ): BlockChairApi

    @Binds
    @Singleton
    fun bindEvmApi(
        impl: EvmApiFactoryImp,
    ): EvmApiFactory

    @Binds
    @Singleton
    fun bindCosmosApi(
        impl: CosmosApiFactoryImp,
    ): CosmosApiFactory

    @Binds
    @Singleton
    fun bindSolanaApi(
        impl: SolanaApiImp,
    ): SolanaApi

    @Binds
    @Singleton
    fun bindPolkadotApi(
        impl: PolkadotApiImp
    ): PolkadotApi

    @Binds
    @Singleton
    fun bindOneInchApi(
        impl: OneInchApiImpl
    ): OneInchApi

    @Binds
    @Singleton
    fun bindLiFiChainApi(
        impl: LiFiChainApiImpl
    ): LiFiChainApi

    @Binds
    @Singleton
    fun bindBlowfishApi(
        impl: BlowfishApiImpl
    ): BlowfishApi
}