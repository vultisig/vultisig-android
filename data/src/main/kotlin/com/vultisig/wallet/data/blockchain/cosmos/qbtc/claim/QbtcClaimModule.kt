package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface QbtcClaimModule {

    @Binds @Singleton fun bindQbtcProofService(impl: QbtcProofServiceImpl): QbtcProofService

    @Binds
    @Singleton
    fun bindQbtcClaimChainService(impl: QbtcClaimChainServiceImpl): QbtcClaimChainService

    @Binds
    @Singleton
    fun bindQbtcClaimableUtxosService(
        impl: QbtcClaimableUtxosServiceImpl
    ): QbtcClaimableUtxosService

    @Binds
    @Singleton
    fun bindLoadClaimableQbtcUtxosUseCase(
        impl: LoadClaimableQbtcUtxosUseCaseImpl
    ): LoadClaimableQbtcUtxosUseCase

    @Binds
    @Singleton
    fun bindComputeQbtcClaimMessageHashUseCase(
        impl: ComputeQbtcClaimMessageHashUseCaseImpl
    ): ComputeQbtcClaimMessageHashUseCase
}
