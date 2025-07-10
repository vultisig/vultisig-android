package com.vultisig.wallet.data.securityscanner

interface SecurityScannerContract {
    suspend fun scanTransaction(transaction: SecurityScannerTransaction): SecurityScannerResult
    suspend fun isSecurityServiceEnabled(): Boolean
    fun getDisabledProviders(): List<String>
    fun getEnabledProviders(): List<String>
    fun disableProviders(providersToDisable: List<String>)
    fun enableProviders(providersToEnable: List<String>)
    fun getSupportedChainsByFeature(): List<SecurityScannerSupport>
}