package com.vultisig.wallet.app

object WalletCoreLoader {
    init {
        System.loadLibrary("TrustWalletCore")
    }
}