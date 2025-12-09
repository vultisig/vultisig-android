package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.usecases.backup.CreateVaultBackupFileNameUseCase
import com.vultisig.wallet.data.usecases.backup.CreateVaultBackupFileNameUseCaseImpl
import com.vultisig.wallet.data.usecases.backup.CreateZipVaultBackupFileNameUseCase
import com.vultisig.wallet.data.usecases.backup.CreateZipVaultBackupFileNameUseCaseImpl
import com.vultisig.wallet.data.usecases.backup.IsVaultBackupFileExtensionValidUseCase
import com.vultisig.wallet.data.usecases.backup.IsVaultBackupFileExtensionValidUseCaseImpl
import com.vultisig.wallet.data.usecases.chaintokens.GetChainTokensUseCase
import com.vultisig.wallet.data.usecases.chaintokens.GetChainTokensUseCaseImpl
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCaseImpl
import com.vultisig.wallet.data.usecases.tss.PullTssMessagesUseCase
import com.vultisig.wallet.data.usecases.tss.PullTssMessagesUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataUsecasesModule {

    @Binds
    @Singleton
    fun bindBroadcastTxUseCase(
        impl: BroadcastTxUseCaseImpl
    ): BroadcastTxUseCase

    @Binds
    @Singleton
    fun bindEncryption(
        impl: AesEncryption
    ): Encryption

    @Binds
    @Singleton
    fun bindDiscoverTokenUseCase(
        impl: DiscoverTokenUseCaseImpl
    ): DiscoverTokenUseCase

    @Binds
    @Singleton
    fun bindDiscoverParticipantsUseCase(
        impl: DiscoverParticipantsUseCaseImpl
    ): DiscoverParticipantsUseCase

    @Binds
    @Singleton
    fun bindPullTssMessagesUseCase(
        impl: PullTssMessagesUseCaseImpl
    ): PullTssMessagesUseCase

    @Binds
    @Singleton
    fun bindGenerateRandomName(
        impl: GenerateRandomUniqueNameImpl
    ): GenerateRandomUniqueName

    @Binds
    @Singleton
    fun bindGenerateUniqueName(
        impl: GenerateUniqueNameImpl
    ): GenerateUniqueName

    @Binds
    @Singleton
    fun bindIsVaultNameValid(
        impl: IsVaultNameValidImpl
    ): IsVaultNameValid

    @Binds
    @Singleton
    fun bindMakeQrCodeBitmapShareFormat(
        impl: MakeQrCodeBitmapShareFormatImpl,
    ): MakeQrCodeBitmapShareFormat

    @Binds
    @Singleton
    fun bindGenerateQrCodeBitmap(
        impl: GenerateQrBitmapImpl,
    ): GenerateQrBitmap

    @Binds
    @Singleton
    fun bindGetOrderedVaults(
        impl: GetOrderedVaultsImpl
    ): GetOrderedVaults

    @Binds
    @Singleton
    fun bindConvertWeiToGwei(
        impl: ConvertWeiToGweiUseCaseImpl
    ): ConvertWeiToGweiUseCase

    @Binds
    @Singleton
    fun bindConvertGweiToWei(
        impl: ConvertGweiToWeiUseCaseImpl
    ): ConvertGweiToWeiUseCase

    @Binds
    @Singleton
    fun bindAvailableTokenBalanceUseCase(
        impl: GetAvailableTokenBalanceUseCaseImpl
    ): GetAvailableTokenBalanceUseCase

    @Binds
    @Singleton
    fun bindSearchTokenUseCase(
        impl: SearchTokenUseCaseImpl
    ): SearchTokenUseCase

    @Binds
    @Singleton
    fun bindSearchSolTokenUseCase(
        impl: SearchSolTokenUseCaseImpl
    ): SearchSolTokenUseCase

    @Binds
    @Singleton
    fun bindSearchEvmTokenUseCase(
        impl: SearchEvmTokenUseCaseImpl
    ): SearchEvmTokenUseCase

    @Binds
    @Singleton
    fun bindGenerateServerPartyId(
        impl: GenerateServerPartyIdImpl
    ): GenerateServerPartyId

    @Binds
    @Singleton
    fun bindGenerateServiceName(
        impl: GenerateServiceNameImpl
    ): GenerateServiceName

    @Binds
    @Singleton
    fun bindInitializeThorChainNetworkIdUseCase(
        impl: InitializeThorChainNetworkIdUseCaseImpl
    ): InitializeThorChainNetworkIdUseCase

    @Binds
    @Singleton
    fun bindNeverShowGlobalBackupReminder(
        impl: NeverShowGlobalBackupReminderUseCaseImpl
    ): NeverShowGlobalBackupReminderUseCase

    @Binds
    @Singleton
    fun bindThorchainBondUseCase(
        impl: ThorchainBondUseCaseImpl
    ): ThorchainBondUseCase

    @Binds
    @Singleton
    fun bindGetGlobalBackupReminderStatus(
        impl: IsGlobalBackupReminderRequiredUseCaseImpl
    ): IsGlobalBackupReminderRequiredUseCase

    @Binds
    @Singleton
    fun bindIsVaultHasFastSignByIdUseCase(
        impl: IsVaultHasFastSignByIdUseCaseImpl
    ): IsVaultHasFastSignByIdUseCase

    @Binds
    @Singleton
    fun bindIsVaultHasFastSignUseCase(
        impl: IsVaultHasFastSignUseCaseImpl
    ): IsVaultHasFastSignUseCase

    @Binds
    @Singleton
    fun bindSearchKujiraTokenUseCase(
        impl: SearchKujiraTokenUseCaseImpl
    ): SearchKujiraTokenUseCase

    @Binds
    @Singleton
    fun bindCreateVaultBackupFileNameUseCase(
        impl: CreateVaultBackupFileNameUseCaseImpl
    ): CreateVaultBackupFileNameUseCase

    @Binds
    @Singleton
    fun bindIsVaultBackupFileExtensionValidUseCase(
        impl: IsVaultBackupFileExtensionValidUseCaseImpl
    ): IsVaultBackupFileExtensionValidUseCase


    @Binds
    @Singleton
    fun bindOneInchToCoinsUseCase(
        impl: OneInchToCoinsUseCaseImpl
    ): OneInchToCoinsUseCase

    @Binds
    @Singleton
    fun bindGetChainTokenUseCase(
        impl: GetChainTokensUseCaseImpl
    ): GetChainTokensUseCase

    @Binds
    @Singleton
    fun bindCreateZipVaultBackupFileNameUseCase(
        impl: CreateZipVaultBackupFileNameUseCaseImpl,
    ): CreateZipVaultBackupFileNameUseCase

    @Binds
    @Singleton
    fun bindValidateMayaTransactionHeightUseCase(
        impl: ValidateMayaTransactionHeightUseCaseImpl,
    ): ValidateMayaTransactionHeightUseCase

}