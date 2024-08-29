package com.vultisig.wallet.data.mappers.utils

import javax.inject.Inject

interface MapHexToPlainString : (String) -> String

internal class MapHexToPlainStringImpl @Inject constructor() : MapHexToPlainString {
    override fun invoke(hex: String): String {
        require(hex.length % 2 == 0) { "Must have an even length" }
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
            .toString(Charsets.ISO_8859_1)  // Or whichever encoding your input uses
    }
}