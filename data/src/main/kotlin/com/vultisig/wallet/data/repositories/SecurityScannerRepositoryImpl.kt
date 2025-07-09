package com.vultisig.wallet.data.repositories

import android.content.SharedPreferences
import androidx.core.content.edit
import jakarta.inject.Inject

interface SecurityScannerRepository {
    fun getSecurityScannerStatus(): Boolean
    fun saveSecurityScannerStatus(enable: Boolean)
}

internal class SecurityScannerRepositoryImpl @Inject constructor(
    private val encryptedSharedPreferences: SharedPreferences
): SecurityScannerRepository {
    override fun getSecurityScannerStatus(): Boolean {
        return encryptedSharedPreferences.getBoolean(SECURITY_SCANNER_KEY, true)
    }

    override fun saveSecurityScannerStatus(enable: Boolean) {
        encryptedSharedPreferences.edit { putBoolean(SECURITY_SCANNER_KEY, enable) }
    }

    private companion object {
        const val SECURITY_SCANNER_KEY = "security_scanner"
    }
}