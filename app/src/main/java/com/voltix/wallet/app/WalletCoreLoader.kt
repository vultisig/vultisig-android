package com.voltix.wallet.app

object WalletCoreLoader {
    init {
        System.loadLibrary("TrustWalletCore")
    }
}