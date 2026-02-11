package com.vultisig.wallet.data.usecases.txstatus

import com.vultisig.wallet.data.api.txstatus.CardanoStatusProvider
import com.vultisig.wallet.data.api.txstatus.CosmosStatusProvider
import com.vultisig.wallet.data.api.txstatus.EvmStatusProvider
import com.vultisig.wallet.data.api.txstatus.PolkadotStatusProvider
import com.vultisig.wallet.data.api.txstatus.RippleStatusProvider
import com.vultisig.wallet.data.api.txstatus.SolanaStatusProvider
import com.vultisig.wallet.data.api.txstatus.SuiStatusProvider
import com.vultisig.wallet.data.api.txstatus.ThorMayaChainStatusProvider
import com.vultisig.wallet.data.api.txstatus.TonStatusProvider
import com.vultisig.wallet.data.api.txstatus.TronStatusProvider
import com.vultisig.wallet.data.api.txstatus.UtxoStatusProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
internal interface TxStatusModule {

    @Binds
    @Singleton
    fun bindTransactionStatusRepository(
        impl: TransactionStatusRepositoryImpl
    ): TransactionStatusRepository

    @EvmTxStatus
    @Binds
    @Singleton
    fun bindEvmStatusProvider(
        impl: EvmStatusProvider
    ): TransactionStatusProvider

    @UtxoTxStatus
    @Binds
    @Singleton
    fun bindUtxoStatusProvider(
        impl: UtxoStatusProvider
    ): TransactionStatusProvider

    @CosmosTxStatus
    @Binds
    @Singleton
    fun bindCosmosStatusProvider(
        impl: CosmosStatusProvider
    ): TransactionStatusProvider

    @ThorChainTxStatus
    @Binds
    @Singleton
    fun bindThorChainStatusProvider(
        impl: ThorMayaChainStatusProvider
    ): TransactionStatusProvider

    @SolanaTxStatus
    @Binds
    @Singleton
    fun bindSolanaStatusProvider(
        impl: SolanaStatusProvider
    ): TransactionStatusProvider

    @SuiTxStatus
    @Binds
    @Singleton
    fun bindSuiStatusProvider(
        impl: SuiStatusProvider
    ): TransactionStatusProvider

    @TonTxStatus
    @Binds
    @Singleton
    fun bindTonStatusProvider(
        impl: TonStatusProvider
    ): TransactionStatusProvider

    @PolkadotTxStatus
    @Binds
    @Singleton
    fun bindPolkadotStatusProvider(
        impl: PolkadotStatusProvider
    ): TransactionStatusProvider

    @CardanoTxStatus
    @Binds
    @Singleton
    fun bindCardanoStatusProvider(
        impl: CardanoStatusProvider
    ): TransactionStatusProvider

    @RippleTxStatus
    @Binds
    @Singleton
    fun bindRippleStatusProvider(
        impl: RippleStatusProvider
    ): TransactionStatusProvider

    @TronTxStatus
    @Binds
    @Singleton
    fun bindTronStatusProvider(
        impl: TronStatusProvider
    ): TransactionStatusProvider

    @Binds
    @Singleton
    fun bindTxStatusConfigurationProvider(
        impl: TxStatusConfigurationProviderImpl
    ): TxStatusConfigurationProvider

    @Binds
    @Singleton
    fun bindPollingTxStatusUseCase(
        impl: PollingTxStatusUseCaseImpl
    ): PollingTxStatusUseCase
}


@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EvmTxStatus
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UtxoTxStatus
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CosmosTxStatus
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ThorChainTxStatus
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SolanaTxStatus
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SuiTxStatus
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TonTxStatus

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PolkadotTxStatus

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CardanoTxStatus

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RippleTxStatus

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TronTxStatus




