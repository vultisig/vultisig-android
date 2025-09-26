package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService
import com.vultisig.wallet.data.blockchain.polkadot.PolkadotFeeService
import com.vultisig.wallet.data.blockchain.sui.SuiFeeService
import com.vultisig.wallet.data.blockchain.ton.TonFeeService
import com.vultisig.wallet.data.blockchain.tron.TronFeeService
import com.vultisig.wallet.data.blockchain.xrp.RippleFeeService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FeeServiceProvidersModule {
    
    @Provides
    @Singleton
    @EthereumFee
    fun provideEthereumFeeService(
        evmApiFactory: EvmApiFactory
    ): FeeService = EthereumFeeService(evmApiFactory)
    
    @Provides
    @Singleton
    @PolkadotFee
    fun providePolkadotFeeService(
        polkadotApi: PolkadotApi
    ): FeeService = PolkadotFeeService(polkadotApi)
    
    @Provides
    @Singleton
    @RippleFee
    fun provideRippleFeeService(
        rippleApi: RippleApi
    ): FeeService = RippleFeeService(rippleApi)
    
    @Provides
    @Singleton
    @SuiFee
    fun provideSuiFeeService(
        suiApi: SuiApi
    ): FeeService = SuiFeeService(suiApi)

    @Provides
    @Singleton
    @TronFee
    fun provideTronFeeService(
        tronApi: TronApi
    ): FeeService = TronFeeService(tronApi)

    @Provides
    @Singleton
    @TonFee
    fun provideTonFeeService(): FeeService = TonFeeService()
}