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

internal fun List<String>.groupByTwoButKeepFirstElement(): List<String> {
    val listSize = this.size
    val originalList = this
    return mutableListOf<String>().apply {
        if (listSize < 1) return this
        add(originalList[0])
        var i = 1
        while (true) {
            val first = originalList.getOrNull(i)
            val second = originalList.getOrNull(i + 1)
            if (first == null) break
            if (second == null) {
                add(first)
                break
            }

            add("$first, $second")
            i += 2
        }
    }
}

internal fun String.minifyAddress(numSymbolsKeep: Int = 10): String {
    return "${substring(0, numSymbolsKeep)}...${substring(length - numSymbolsKeep)}"
}