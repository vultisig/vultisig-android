package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidRpcClient
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidRpcClientContract
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidScannerService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface SecurityScannerModule {
    @Binds
    @Singleton
    fun bindBlockhaidRpcClient(
        impl: BlockaidRpcClient
    ): BlockaidRpcClientContract

    @Binds
    @IntoSet
    @Singleton
    fun bindBlockaidScannerService(
        service: BlockaidScannerService
    ): ProviderScannerServiceContract

    @Binds
    @Singleton
    fun bindSecurityScanner(
        impl: SecurityScannerService
    ): SecurityScannerContract
}