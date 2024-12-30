package com.vultisig.wallet.data.keygen

import kotlin.math.ceil

object DklsHelper {
    fun getThreshold(input: Int): Long {
        return ceil(input * 2.0 / 3.0).toLong()
    }

    fun arrayToBytes(parties: List<String>): ByteArray {
        if (parties.isEmpty()) {
            return byteArrayOf()
        }
        val byteArray = mutableListOf<Byte>()
        for (item in parties) {
            val utf8Bytes = item.toByteArray(Charsets.UTF_8)
            byteArray.addAll(utf8Bytes.toList())
            byteArray.add(0)
        }
        if (byteArray.last() == 0.toByte()) {
            byteArray.removeAt(byteArray.size - 1)
        }
        return byteArray.toByteArray()
    }
}