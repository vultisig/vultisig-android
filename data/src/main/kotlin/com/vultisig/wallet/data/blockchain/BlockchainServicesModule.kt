package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.ethereum.CircleDeFiBalanceService
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService
import com.vultisig.wallet.data.blockchain.thorchain.DefaultStakingPositionService
import com.vultisig.wallet.data.blockchain.thorchain.RujiStakingService
import com.vultisig.wallet.data.blockchain.thorchain.TCYStakingService
import com.vultisig.wallet.data.blockchain.thorchain.ThorchainDeFiBalanceService
import com.vultisig.wallet.data.repositories.ActiveBondedNodeRepository
import com.vultisig.wallet.data.repositories.ScaCircleAccountRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
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
        fun bindTierRemoteNFTService(
            factory: EvmApiFactory
        ): TierRemoteNFTService = TierRemoteNFTServiceImpl(factory)

        @Provides
        @Singleton
        fun provideRujiStakingService(
            thorChainApi: ThorChainApi,
            stakingDetailsRepository: StakingDetailsRepository
        ): RujiStakingService = RujiStakingService(
            thorChainApi,
            stakingDetailsRepository
        )

        @Provides
        @Singleton
        fun provideTCYStakingService(
            thorChainApi: ThorChainApi,
            tokenPriceRepository: TokenPriceRepository,
            stakingDetailsRepository: StakingDetailsRepository
        ): TCYStakingService = TCYStakingService(
            thorChainApi,
            tokenPriceRepository,
            stakingDetailsRepository
        )

        @Provides
        @Singleton
        fun provideDefaultStakingPositionService(
            thorChainApi: ThorChainApi,
            stakingDetailsRepository: StakingDetailsRepository,
        ): DefaultStakingPositionService = DefaultStakingPositionService(
            thorChainApi,
            stakingDetailsRepository,
        )

        @Provides
        @Singleton
        fun provideThorchainDeFiBalanceService(
            rujiStakingService: RujiStakingService,
            tcyStakingService: TCYStakingService,
            defaultStakingPositionService: DefaultStakingPositionService,
            bondUseCase: ThorchainBondUseCase,
            stakingDetailsRepository: StakingDetailsRepository,
            activeBondedNodeRepository: ActiveBondedNodeRepository
        ): ThorchainDeFiBalanceService = ThorchainDeFiBalanceService(
            rujiStakingService = rujiStakingService,
            tcyStakingService = tcyStakingService,
            defaultStakingPositionService = defaultStakingPositionService,
            bondUseCase = bondUseCase,
            stakingDetailsRepository = stakingDetailsRepository,
            activeBondedNodeRepository = activeBondedNodeRepository,
        )

        @Provides
        @Singleton
        fun provideCircleDeFiBalanceService(
            stakingDetailsRepository: StakingDetailsRepository,
            scaCircleAccountRepository: ScaCircleAccountRepository,
            evmApiFactory: EvmApiFactory,
        ): CircleDeFiBalanceService = CircleDeFiBalanceService(
            stakingDetailsRepository = stakingDetailsRepository,
            scaCircleAccountRepository = scaCircleAccountRepository,
            evmApi = evmApiFactory,
        )
    }
}