package com.vultisig.wallet.app

object WalletCoreLoader {
    init {
        System.loadLibrary("TrustWalletCore")
        System.loadLibrary("godklsswig")
        System.loadLibrary("goschnorrswig")
        System.loadLibrary("godilithiumswig")
    }
}