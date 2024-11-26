package com.vultisig.wallet.data.common

fun String.isJson(): Boolean {
    return startsWith("{") && endsWith("}")
}