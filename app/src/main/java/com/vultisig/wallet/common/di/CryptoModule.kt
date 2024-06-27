package com.vultisig.wallet.common.di

import com.vultisig.wallet.common.AESCryptoManager
import com.vultisig.wallet.common.CryptoManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface CryptoModule {
    @Binds
    fun bindCryptoManager(aesCryptoManager: AESCryptoManager): CryptoManager
}

