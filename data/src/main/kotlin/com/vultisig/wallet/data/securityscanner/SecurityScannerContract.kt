package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.Transaction

interface SecurityScannerContract {
    suspend fun scanTransaction(transaction: SecurityScannerTransaction): SecurityScannerResult
    suspend fun isSecurityServiceEnabled(): Boolean
    suspend fun createSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction
    suspend fun createSecurityScannerTransaction(transaction: SwapTransaction): SecurityScannerTransaction
    fun getDisabledProviders(): List<String>
    fun getEnabledProviders(): List<String>
    fun disableProviders(providersToDisable: List<String>)
    fun enableProviders(providersToEnable: List<String>)
    fun getSupportedChainsByFeature(): List<SecurityScannerSupport>
}