package com.vultisig.wallet.data.usecases.fast

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FastVaultModule {

    @Binds
    @Singleton
    internal abstract fun bindVerifyFastVaultBackupCodeUseCase(
        impl: VerifyFastVaultBackupCodeUseCaseImpl,
    ): VerifyFastVaultBackupCodeUseCase

}