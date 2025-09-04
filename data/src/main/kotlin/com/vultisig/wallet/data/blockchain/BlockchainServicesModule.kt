package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService
import dagger.Binds
import dagger.Module
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
}