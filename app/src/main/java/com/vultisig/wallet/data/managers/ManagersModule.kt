package com.vultisig.wallet.data.managers

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ManagersModule {
    @Binds
    @Singleton
    fun bindVaultDataStoreManager(
        impl: VaultDataStoreManagerImpl
    ): VaultDataStoreManager
}