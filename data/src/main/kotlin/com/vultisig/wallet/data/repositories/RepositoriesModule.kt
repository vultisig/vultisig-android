package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import com.vultisig.wallet.data.db.models.FolderOrderEntity
import com.vultisig.wallet.data.db.models.VaultOrderEntity
import com.vultisig.wallet.data.repositories.onboarding.OnboardingRepository
import com.vultisig.wallet.data.repositories.onboarding.OnboardingRepositoryImpl
import com.vultisig.wallet.data.repositories.order.AddressBookOrderRepository
import com.vultisig.wallet.data.repositories.order.FolderOrderRepository
import com.vultisig.wallet.data.repositories.order.OrderRepository
import com.vultisig.wallet.data.repositories.order.VaultOrderRepository
import com.vultisig.wallet.data.repositories.vault.TemporaryVaultRepository
import com.vultisig.wallet.data.repositories.vault.TemporaryVaultRepositoryImpl
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepoImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface RepositoriesModule {

    @Binds
    @Singleton
    fun bindCustomMessagePayloadRepo(
        impl: CustomMessagePayloadRepoImpl
    ): CustomMessagePayloadRepo

    @Binds
    @Singleton
    fun bindChainAccountAddressRepository(
        impl: ChainAccountAddressRepositoryImpl,
    ): ChainAccountAddressRepository

    @Binds
    @Singleton
    fun bindTokenRepository(
        impl: TokenRepositoryImpl,
    ): TokenRepository

    @Binds
    @Singleton
    fun bindVaultRepository(
        impl: VaultRepositoryImpl
    ): VaultRepository

    @Binds
    @Singleton
    fun bindVultiSignerRepository(
        impl: VultiSignerRepositoryImpl
    ): VultiSignerRepository

    @Binds
    @Singleton
    fun bindVaultDataStoreRepository(
        impl: VaultDataStoreRepositoryImpl
    ): VaultDataStoreRepository

    @Binds
    @Singleton
    fun bindTransactionRepository(
        impl: TransactionRepositoryImpl,
    ): TransactionRepository

    @Binds
    @Singleton
    fun bindAppCurrencyRepository(
        impl: AppCurrencyRepositoryImpl,
    ): AppCurrencyRepository

    @Binds
    @Singleton
    fun bindTokenPriceRepository(
        impl: TokenPriceRepositoryImpl,
    ): TokenPriceRepository

    @Binds
    @Singleton
    fun bindAppLocaleRepository(
        impl: AppLocaleRepositoryImpl,
    ): AppLocaleRepository

   @Binds
   @Singleton
   fun bindReferralCodeRepository(
       impl: ReferralCodeSettingsRepository
   ): ReferralCodeSettingsRepositoryContract

    @Binds
    @Singleton
    fun bindAddressBookEntryRepository(
        impl: AddressBookRepositoryImpl
    ): AddressBookRepository

    @Binds
    @Singleton
    fun bindAddressParserRepository(
        impl: AddressParserRepositoryImpl
    ): AddressParserRepository

    @Binds
    @Singleton
    fun bindAllowanceRepository(
        impl: AllowanceRepositoryImpl,
    ): AllowanceRepository

    @Binds
    @Singleton
    fun bindBalanceRepositoryImpl(
        impl: BalanceRepositoryImpl,
    ): BalanceRepository

    @Binds
    @Singleton
    fun bindSPLTokenRepository(
        impl: SplTokenRepositoryImpl
    ): SplTokenRepository

    @Binds
    @Singleton
    fun bindBalanceVisibilityRepository(
        impl: BalanceVisibilityRepositoryImpl
    ): BalanceVisibilityRepository

    @Binds
    @Singleton
    fun bindBlockChainSpecificRepository(
        impl: BlockChainSpecificRepositoryImpl,
    ): BlockChainSpecificRepository

    @Binds
    @Singleton
    fun bindDefaultChainsRepository(
        impl: DefaultChainsRepositoryImpl,
    ): DefaultChainsRepository

    @Binds
    @Singleton
    fun bindDepositTransactionRepository(
        impl: DepositTransactionRepositoryImpl
    ): DepositTransactionRepository

    @Binds
    @Singleton
    fun bindExplorerLinkRepository(
        impl: ExplorerLinkRepositoryImpl,
    ): ExplorerLinkRepository

    @Binds
    @Singleton
    fun bindGasFeesRepository(
        impl: GasFeeRepositoryImpl,
    ): GasFeeRepository

    @Binds
    @Singleton
    fun bindLastOpenedVaultRepository(
        impl: LastOpenedVaultRepositoryImpl,
    ): LastOpenedVaultRepository

    @Binds
    @Singleton
    fun bindVaultOrderRepository(
        impl: VaultOrderRepository
    ): OrderRepository<VaultOrderEntity>

    @Binds
    @Singleton
    fun bindFolderOrderRepository(
        impl: FolderOrderRepository
    ): OrderRepository<FolderOrderEntity>

    @Binds
    @Singleton
    fun bindAddressBookOrderRepository(
        impl: AddressBookOrderRepository
    ): OrderRepository<AddressBookOrderEntity>

    @Binds
    @Singleton
    fun bindRequestResultRepository(
        impl: RequestResultRepositoryImpl
    ): RequestResultRepository

    @Binds
    @Singleton
    fun bindSwapQuoteRepository(
        impl: SwapQuoteRepositoryImpl
    ): SwapQuoteRepository

    @Binds
    @Singleton
    fun bindChainAccountsRepository(
        impl: AccountsRepositoryImpl,
    ): AccountsRepository

    @Binds
    @Singleton
    fun bindSwapTransactionRepository(
        impl: SwapTransactionRepositoryImpl
    ): SwapTransactionRepository

    @Singleton
    @Binds
    fun bindOnBoardRepository(
        impl: OnboardingRepositoryImpl,
    ): OnboardingRepository

    @Singleton
    @Binds
    fun bindFolderRepository(
        impl: FolderRepositoryImpl,
    ): FolderRepository

    @Singleton
    @Binds
    fun bindVaultPasswordRepository(
        impl: VaultPasswordRepositoryImpl,
    ): VaultPasswordRepository

    @Singleton
    @Binds
    fun bindSecurityScannerRepository(
        impl: OnChainSecurityScannerRepositoryImpl,
    ): OnChainSecurityScannerRepository

    @Binds
    @Singleton
    fun bindThorChainRepository(
        impl: ThorChainRepositoryImpl
    ): ThorChainRepository

    @Binds
    @Singleton
    fun bindVaultMetadataRepo(
        impl: VaultMetadataRepoImpl
    ): VaultMetadataRepo

    @Binds
    @Singleton
    fun bindFourByteRepository(
        impl: FourByteRepositoryImpl
    ): FourByteRepository

    @Binds
    @Singleton
    fun bindSecretSettingsRepository(
        impl: SecretSettingsRepositoryImpl
    ): SecretSettingsRepository

    @Binds
    @Singleton
    fun bindQrHelperModalRepository(
        impl: QrHelperModalRepositoryImpl
    ): QrHelperModalRepository

    @Binds
    @Singleton
    fun bindTemporaryVaultRepository(
        impl: TemporaryVaultRepositoryImpl
    ): TemporaryVaultRepository

    @Binds
    @Singleton
    fun bindCryptoConnectionTypeRepository(
        impl: CryptoConnectionTypeRepositoryImpl
    ): CryptoConnectionTypeRepository

    @Binds
    @Singleton
    fun bindBondedNodesRepository(
        impl: BondedNodesRepositoryImpl
    ): BondedNodesRepository
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PrettyJson