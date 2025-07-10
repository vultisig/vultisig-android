package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Transaction

interface SecurityScannerContract {
    suspend fun scanTransaction(transaction: SecurityScannerTransaction): SecurityScannerResult
    suspend fun isSecurityServiceEnabled(): Boolean
    fun createSecurityScannerTransaction(transaction: Transaction): SecurityScannerTransaction
    fun getDisabledProviders(): List<String>
    fun getEnabledProviders(): List<String>
    fun disableProviders(providersToDisable: List<String>)
    fun enableProviders(providersToEnable: List<String>)
    fun getSupportedChainsByFeature(): List<SecurityScannerSupport>
}