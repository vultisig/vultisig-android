package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.repositories.OnChainSecurityScannerRepository
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidRpcClient
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidRpcClientContract
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidScannerService
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidSimulationService
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidSimulationServiceImpl
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
    fun provideSecurityScannerTransactionFactory(
        solanaApi: SolanaApi,
        suiApi: SuiApi,
    ): SecurityScannerTransactionFactoryContract {
        return SecurityScannerTransactionFactory(solanaApi, suiApi)
    }

    @Singleton
    @Provides
    fun provideBlockaidRpcClient(httpClient: HttpClient): BlockaidRpcClientContract {
        return BlockaidRpcClient(httpClient)
    }

    @Singleton
    @Provides
    fun provideBlockaidScannerService(
        blockaidRpcClient: BlockaidRpcClientContract
    ): ProviderScannerServiceContract {
        return BlockaidScannerService(blockaidRpcClient)
    }

    @Singleton
    @Provides
    fun provideSecurityScannerService(
        blockaidScannerService: ProviderScannerServiceContract,
        onChainSecurityScannerRepository: OnChainSecurityScannerRepository,
        securityScannerTransactionFactory: SecurityScannerTransactionFactoryContract,
    ): SecurityScannerContract {
        val providers = listOf(blockaidScannerService)
        return SecurityScannerService(
            providers,
            onChainSecurityScannerRepository,
            securityScannerTransactionFactory,
        )
    }

    @Singleton
    @Provides
    fun provideBlockaidSimulationService(
        blockaidRpcClient: BlockaidRpcClientContract
    ): BlockaidSimulationService {
        // Singleton-scoped on purpose: the cache is the contract — verify and
        // done screens own different ViewModels but must see the same scan.
        return BlockaidSimulationServiceImpl(blockaidRpcClient)
    }
}
