package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain

interface SecurityScannerServiceContract {
    suspend fun scanTransaction(transaction: SecurityScannerTransaction)
    fun getProviderName(): String
    fun supportsChain(chain: Chain): Boolean
    fun getSupportedChains(): List<Chain>
}