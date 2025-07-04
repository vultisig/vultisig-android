package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain

interface ProviderScannerServiceContract {
    suspend fun scanTransaction(transaction: SecurityScannerTransaction): SecurityScannerResult
    fun getProviderName(): String
    fun supportsChain(chain: Chain, feature: SecurityScannerFeaturesType): Boolean
    fun getSupportedChains(): Map<SecurityScannerFeaturesType, List<Chain>>
    fun getSupportedFeatures(): List<SecurityScannerFeaturesType>
}