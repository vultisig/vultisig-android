package com.vultisig.wallet.data.passcode

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Wires the passcode app-lock dependencies. */
@Module
@InstallIn(SingletonComponent::class)
internal interface PasscodeModule {

    @Binds fun bindPasscodeConfig(impl: PasscodeConfigImpl): PasscodeConfig

    @Binds fun bindPasscodeStore(impl: SharedPreferencesPasscodeStore): PasscodeStore

    @Binds fun bindBiometricUnlockStore(impl: KeyStoreBiometricUnlockStore): BiometricUnlockStore

    @Binds fun bindPasscodeRepository(impl: PasscodeRepositoryImpl): PasscodeRepository

    // Same @Singleton-scoped instance as the repository binding above: the in-memory data key must
    // be shared, not re-created per injection point.
    @Binds fun bindPasscodeDataKeySource(impl: PasscodeRepositoryImpl): PasscodeDataKeySource

    @Binds fun bindAutoLockRepository(impl: AutoLockRepositoryImpl): AutoLockRepository

    @Binds
    fun bindVaultKeyShareProtection(impl: VaultKeyShareProtectionImpl): VaultKeyShareProtection
}
