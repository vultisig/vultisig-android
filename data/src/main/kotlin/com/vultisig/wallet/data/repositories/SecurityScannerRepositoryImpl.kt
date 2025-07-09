package com.vultisig.wallet.data.repositories

interface SecurityScannerRepository {
    fun getSecurityScannerStatus(): Boolean

}

internal class SecurityScannerRepositoryImpl {

}