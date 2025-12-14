package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.SuiApiImpl
import com.vultisig.wallet.data.api.chains.TonApi
import com.vultisig.wallet.data.api.chains.TonApiImpl
import com.vultisig.wallet.data.api.swapAggregators.KyberApi
import com.vultisig.wallet.data.api.swapAggregators.KyberApiImpl
import com.vultisig.wallet.data.api.swapAggregators.OneInchApi
import com.vultisig.wallet.data.api.swapAggregators.OneInchApiImpl
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
    fun bindSessionApi(
        impl: SessionApiImpl
    ): SessionApi

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
    fun bindLiFiChainApi(
        impl: LiFiChainApiImpl
    ): LiFiChainApi

    @Binds
    @Singleton
    fun bindBlowfishApi(
        impl: BlowfishApiImpl
    ): BlowfishApi

    @Binds
    @Singleton
    fun bindFeatureFlagApi(
        impl: FeatureFlagApiImpl,
    ): FeatureFlagApi

    @Binds
    @Singleton
    fun bindSuiApi(
        impl: SuiApiImpl
    ): SuiApi


    @Binds
    @Singleton
    fun bindTonApi(
        impl: TonApiImpl
    ): TonApi

    @Binds
    @Singleton
    fun bindRippleApi(
        impl: RippleApiImp
    ): RippleApi

    @Binds
    @Singleton
    fun bindRouterApi(
        impl: RouterApiImp,
    ): RouterApi

    @Binds
    @Singleton
    fun bindFourByteApi(
        impl: FourByteApiImpl
    ): FourByteApi

    @Binds
    @Singleton
    fun bindLiQuestApi(
        impl: LiQuestApiImpl,
    ): LiQuestApi

    @Binds
    @Singleton
    fun bindJupiterApi(
        impl: JupiterApiImpl,
    ): JupiterApi

    @Binds
    @Singleton
    fun bindTronApi(
        impl: TronApiImpl,
    ): TronApi

    @Binds
    @Singleton
    fun bindCardanoApi(
        impl: CardanoApiImpl,
    ): CardanoApi

    @Binds
    @Singleton
    fun bindKyberApi(
        impl: KyberApiImpl,
    ): KyberApi

    @Binds
    @Singleton
    fun circleApi(
        impl: CircleApiImpl,
    ): CircleApi
}