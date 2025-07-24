package com.vultisig.wallet.common

import org.junit.jupiter.api.Test

class BaseTest {
    companion object {
        init {
            try {
                System.loadLibrary("TrustWalletCore")
                println("WalletCore native library loaded successfully.")
            } catch (e: UnsatisfiedLinkError) {
                println("Failed to load WalletCore native library: ${e.message}")
                throw e
            } catch (e: Exception) {
                println("An unexpected error occurred while loading WalletCore library: ${e.message}")
                throw e
            }
        }
    }

    @Test
    fun testWalletCoreLibraryLoaded() {
         println("Executing testWalletCoreLibraryLoaded...")
    }
}