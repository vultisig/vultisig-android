package com.vultisig.wallet.data.mappers

import com.google.gson.JsonElement
import java.math.BigInteger

internal fun JsonElement.fromIosBigInt(): BigInteger {
    val jsonArray = asJsonArray
    val bigIntegerParts = jsonArray.drop(1).map { it.asBigInteger }

    var bigInteger = BigInteger.ZERO
    for (part in bigIntegerParts) {
        bigInteger = bigInteger.shiftLeft(64).or(part)
    }

    return bigInteger
}