package com.vultisig.wallet.data.usecases.resolveprovider

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ResolveProviderModule {

    @Binds
    @Singleton
    fun bindDefaultSwapProviderSelectionStrategy(
        impl: DefaultSwapProviderSelectionStrategyImpl,
    ): DefaultSwapProviderSelectionStrategy


    @Binds
    @Singleton
    fun bindKyberSwapProviderSelectionStrategy(
        impl: KyberSwapProviderSelectionStrategyImpl,
    ): KyberSwapProviderSelectionStrategy

}
