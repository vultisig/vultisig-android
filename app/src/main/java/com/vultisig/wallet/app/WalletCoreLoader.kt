package com.vultisig.wallet.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WalletCoreLoader {
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            System.loadLibrary("TrustWalletCore")
            System.loadLibrary("godklsswig")
            System.loadLibrary("goschnorrswig")
        }
    }
}