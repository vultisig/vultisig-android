package com.vultisig.wallet.data.blockchain.cosmos.qbtc

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface QbtcNetworkModule {

    @Binds @Singleton fun bindQbtcNetworkCheck(impl: QbtcNetworkCheckImpl): QbtcNetworkCheck
}
