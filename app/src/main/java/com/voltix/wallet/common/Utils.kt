package com.voltix.wallet.common

object Utils {
    val deviceName: String
        get() {
            return "${android.os.Build.MODEL}-${(100..999).random()}"
        }
}