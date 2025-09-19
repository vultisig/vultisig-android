package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService
import com.vultisig.wallet.data.blockchain.polkadot.PolkadotFeeService
import com.vultisig.wallet.data.blockchain.sui.SuiFeeService
import com.vultisig.wallet.data.blockchain.ton.TonFeeService
import com.vultisig.wallet.data.blockchain.tron.TronFeeService
import com.vultisig.wallet.data.blockchain.xrp.XRPFeeService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FeeServiceModule {
    
    @Provides
    @Singleton
    fun provideEthereumFeeService(
        evmApiFactory: EvmApiFactory
    ): FeeService {
        return EthereumFeeService(evmApiFactory)
    }
    
    @Provides
    @Singleton
    fun providePolkadotFeeService(
        polkadotApi: PolkadotApi
    ): FeeService {
        return PolkadotFeeService(polkadotApi)
    }
    
    @Provides
    @Singleton
    fun provideRippleFeeService(
        rippleApi: RippleApi
    ): FeeService {
        return XRPFeeService(rippleApi)
    }
    
    @Provides
    @Singleton
    fun provideSuiFeeService(
        suiApi: SuiApi
    ): FeeService {
        return SuiFeeService(suiApi)
    }
    
    @Provides
    @Singleton
    fun provideTonFeeService(): FeeService {
        return TonFeeService()
    }

    @Provides
    @Singleton
    fun provideFeeService(
        ethereumFeeService: EthereumFeeService,
        polkadotFeeService: PolkadotFeeService,
        rippleFeeService: XRPFeeService,
        suiFeeService: SuiFeeService,
        tonFeeService: TonFeeService,
        tronFeeService: TronFeeService,
    ): FeeService {
        return FeeServiceComposite(
            ethereumFeeService = ethereumFeeService,
            polkadotFeeService = polkadotFeeService,
            rippleFeeService = rippleFeeService,
            suiFeeService = suiFeeService,
            tonFeeService = tonFeeService,
        )
    }
}