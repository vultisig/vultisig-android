package com.vultisig.wallet.ui.utils

internal fun String.getAddressFromQrCode(): String {
    val removedPrefix = if (contains(":")) {
        substringAfter(":")
    } else {
        this
    }
    val removedSuffix = if (contains("?")) {
        removedPrefix.substringBefore("?")
    } else {
        removedPrefix
    }
    return removedSuffix
}

internal fun String.isReshare(): Boolean {
    return contains("tssType=Reshare")
}

internal fun String.isJson(): Boolean {
    return startsWith("{") && endsWith("}")
}