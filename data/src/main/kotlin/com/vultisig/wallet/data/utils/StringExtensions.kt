package com.vultisig.wallet.data.utils

fun String.add0x(): String {
    if (startsWith("0x")){
        return this
    }
    return "0x$this"
}