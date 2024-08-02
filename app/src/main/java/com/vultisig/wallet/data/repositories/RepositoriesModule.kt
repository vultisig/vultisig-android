package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import com.vultisig.wallet.data.db.models.VaultOrderEntity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface RepositoriesModule {

    @Binds
    @Singleton
    fun bindAllowanceRepository(
        impl: AllowanceRepositoryImpl,
    ): AllowanceRepository

    @Binds
    @Singleton
    fun bindAppCurrencyRepository(
        impl: AppCurrencyRepositoryImpl,
    ): AppCurrencyRepository

    @Binds
    @Singleton
    fun bindChainAccountAddressRepository(
        impl: ChainAccountAddressRepositoryImpl,
    ): ChainAccountAddressRepository

    @Binds
    @Singleton
    fun bindTokenPriceRepository(
        impl: TokenPriceRepositoryImpl,
    ): TokenPriceRepository

    @Binds
    @Singleton
    fun bindChainAccountsRepository(
        impl: AccountsRepositoryImpl,
    ): AccountsRepository

    @Binds
    @Singleton
    fun bindBalanceRepositoryImpl(
        impl: BalanceRepositoryImpl,
    ): BalanceRepository

    @Singleton
    @Binds
    abstract fun bindOnBoardRepository(
        impl: OnBoardRepositoryImpl,
    ): OnBoardRepository

    @Binds
    @Singleton
    fun bindTokenRepository(
        impl: TokenRepositoryImpl,
    ): TokenRepository

    @Binds
    @Singleton
    fun bindGasFeesRepository(
        impl: GasFeeRepositoryImpl,
    ): GasFeeRepository

    @Binds
    @Singleton
    fun bindAppLocaleRepository(
        impl: AppLocaleRepositoryImpl,
    ): AppLocaleRepository

    @Binds
    @Singleton
    fun bindDefaultChainsRepository(
        impl: DefaultChainsRepositoryImpl,
    ): DefaultChainsRepository

    @Binds
    @Singleton
    fun bindTransactionRepository(
        impl: TransactionRepositoryImpl,
    ): TransactionRepository

    @Binds
    @Singleton
    fun bindBlockChainSpecificRepository(
        impl: BlockChainSpecificRepositoryImpl,
    ): BlockChainSpecificRepository

    @Binds
    @Singleton
    fun bindExplorerLinkRepository(
        impl: ExplorerLinkRepositoryImpl,
    ): ExplorerLinkRepository

    @Binds
    @Singleton
    fun bindLastOpenedVaultRepository(
        impl: LastOpenedVaultRepositoryImpl,
    ): LastOpenedVaultRepository

    @Binds
    @Singleton
    fun bindVaultRepository(
        impl: VaultRepositoryImpl
    ): VaultRepository

    @Binds
    @Singleton
    fun bindVaultOrderRepository(
        impl: VaultOrderRepository
    ): OrderRepository<VaultOrderEntity>

    @Binds
    @Singleton
    fun bindSwapQuoteRepository(
        impl: SwapQuoteRepositoryImpl
    ): SwapQuoteRepository

    @Binds
    @Singleton
    fun bindSwapTransactionRepository(
        impl: SwapTransactionRepositoryImpl
    ): SwapTransactionRepository

    @Binds
    @Singleton
    fun bindDepositTransactionRepository(
        impl: DepositTransactionRepositoryImpl
    ): DepositTransactionRepository

    @Binds
    @Singleton
    fun bindBalanceVisibilityRepository(
        impl: BalanceVisibilityRepositoryImpl
    ): BalanceVisibilityRepository

    @Binds
    @Singleton
    fun bindVaultDataStoreRepository(
        impl: VaultDataStoreRepositoryImpl
    ): VaultDataStoreRepository

    @Binds
    @Singleton
    fun bindAddressBookEntryRepository(
        impl: AddressBookRepositoryImpl
    ): AddressBookRepository

    @Binds
    @Singleton
    fun bindRequestResultRepository(
        impl: RequestResultRepositoryImpl
    ): RequestResultRepository

    @Binds
    @Singleton
    fun bindAddressBookOrderRepository(
        impl: AddressBookOrderRepository
    ): OrderRepository<AddressBookOrderEntity>

    @Binds
    @Singleton
    fun bindSPLTokenRepository(
        impl: SPLTokenRepositoryImpl
    ): SPLTokenRepository

}