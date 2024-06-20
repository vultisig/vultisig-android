package com.vultisig.wallet.data.mappers

import com.google.gson.JsonArray
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

internal fun BigInteger.toIosBigInt(): JsonElement {
    val parts = mutableListOf<BigInteger>()
    var temp = this

    while (temp != BigInteger.ZERO) {
        parts.add(temp.and(BigInteger("FFFFFFFFFFFFFFFF", 16)))
        temp = temp.shiftRight(64)
    }

    if (parts.isEmpty()) {
        parts.add(BigInteger.ZERO)
    }

    val jsonArray = JsonArray()
    jsonArray.add("+")
    parts.reversed().forEach { jsonArray.add(it) }

    return jsonArray
}