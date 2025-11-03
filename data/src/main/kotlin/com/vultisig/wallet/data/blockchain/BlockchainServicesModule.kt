package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService
import com.vultisig.wallet.data.blockchain.thorchain.TCYStakingService
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface BlockchainServicesModule {
    @Binds
    @Singleton
    fun bindFeeService(
        impl: EthereumFeeService
    ): FeeService
    
    companion object {
        @Provides
        @Singleton
        fun provideRujiStakingService(
            thorChainApi: ThorChainApi
        ): RujiStakingService = RujiStakingService(thorChainApi)
        
        @Provides
        @Singleton
        fun provideTCYStakingService(
            thorChainApi: ThorChainApi,
            tokenPriceRepository: TokenPriceRepository
        ): TCYStakingService = TCYStakingService(thorChainApi, tokenPriceRepository)
    }
}