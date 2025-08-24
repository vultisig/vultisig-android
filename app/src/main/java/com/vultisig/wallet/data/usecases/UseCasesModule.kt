package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.usecases.cosmos.CosmosToCoinsUseCase
import com.vultisig.wallet.data.usecases.resolveprovider.ResolveProviderUseCase
import com.vultisig.wallet.data.usecases.resolveprovider.ResolveProviderUseCaseImpl
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface UseCasesModule {

    @Binds
    @Singleton
    fun bindConvertTokenValueToFiatUseCase(
        impl: ConvertTokenValueToFiatUseCaseImpl
    ): ConvertTokenValueToFiatUseCase

    @Binds
    @Singleton
    fun bindConvertTokenAndValueToTokenValueUseCase(
        impl: ConvertTokenAndValueToTokenValueUseCaseImpl
    ): ConvertTokenAndValueToTokenValueUseCase

    @Binds
    @Singleton
    fun bindSaveVaultUseCase(
        impl: SaveVaultUseCaseImpl
    ): SaveVaultUseCase

    @Binds
    @Singleton
    fun bindParseVaultFromStringUseCase(
        impl: ParseVaultFromStringUseCaseImpl
    ): ParseVaultFromStringUseCase

    @Binds
    @Singleton
    fun bindCreateVaultBackupUseCase(
        impl: CreateVaultBackupUseCaseImpl
    ): CreateVaultBackupUseCase

    @Binds
    @Singleton
    fun bindCompressQrUseCase(
        impl: CompressQrUseCaseImpl
    ): CompressQrUseCase

    @Binds
    @Singleton
    fun bindDecompressQrUseCase(
        impl: DecompressQrUseCaseImpl
    ): DecompressQrUseCase

    @Binds
    @Singleton
    fun bindEnableTokenUseCase(
        impl: EnableTokenUseCaseImpl
    ): EnableTokenUseCase

    @Binds
    @Singleton
    fun bindIsAssetsValidUseCase(
        impl: DepositMemoAssetsValidatorUseCaseImpl
    ): DepositMemoAssetsValidatorUseCase

    @Binds
    @Singleton
    fun bindGasFeeToEstimatedFeeUseCase(
        impl: GasFeeToEstimatedFeeUseCaseImpl,
    ): GasFeeToEstimatedFeeUseCase

    @Binds
    @Singleton
    fun bindLaunchKeysignUseCase(
        impl: LaunchKeysignUseCaseImpl
    ): LaunchKeysignUseCase

    @Binds
    @Singleton
    fun bindGetDirectionByQrCodeUseCase(
        impl: GetDirectionByQrCodeUseCaseImpl
    ): GetDirectionByQrCodeUseCase

    @Binds
    @Singleton
    fun bindCreateQrCodeSharingBitmapUseCase(
        impl: CreateQrCodeSharingBitmapUseCaseImpl
    ): CreateQrCodeSharingBitmapUseCase

    @Binds
    @Singleton
    fun bindGetFlowTypeUseCase(
        impl: GetFlowTypeUseCaseImpl
    ): GetFlowTypeUseCase

    @Binds
    @Singleton
    fun bindRequestQrScanUseCase(
        impl: RequestQrScanUseCaseImpl
    ): RequestQrScanUseCase

    @Binds
    @Singleton
    fun bindResolveProviderUseCase(
        impl: ResolveProviderUseCaseImpl
    ): ResolveProviderUseCase

    @Binds
    fun bindCosmosToCoinsUseCase(
        cosmosToCoinsUseCaseImpl: CosmosToCoinsUseCaseImpl
    ): CosmosToCoinsUseCase

}