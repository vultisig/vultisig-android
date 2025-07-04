package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidRpcClient
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidRpcClientContract
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidScannerService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityScannerModule {

    @Singleton
    @Provides
    fun provideBlockaidRpcClient(httpClient: HttpClient): BlockaidRpcClientContract {
        return BlockaidRpcClient(httpClient)
    }

    @Singleton
    @Provides
    fun provideBlockaidScannerService(blockaidRpcClient: BlockaidRpcClientContract): ProviderScannerServiceContract {
        return BlockaidScannerService(blockaidRpcClient)
    }

    @Singleton
    @Provides
    fun provideSecurityScannerService(
        blockaidScannerService: ProviderScannerServiceContract
    ): SecurityScannerContract {
        val providers = listOf(blockaidScannerService)
        return SecurityScannerService(providers)
    }
}