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

    // Function to encode an integer as ASN.1 DER
    fun encodeASN1Integer(value: ByteArray): ByteArray {
        val encoded = mutableListOf<Byte>()
        encoded.add(0x02) // ASN.1 INTEGER tag
        if (value.first() >= 0x80.toByte()) {
            encoded.add((value.size + 1).toByte())
            encoded.add(0x00)
        } else {
            encoded.add(value.size.toByte())
        }
        encoded.addAll(value.toList())
        return encoded.toByteArray()
    }

    // Function to create a DER-encoded ECDSA signature
    fun createDERSignature(r: ByteArray, s: ByteArray): ByteArray {
        val encodedR = encodeASN1Integer(r)
        val encodedS = encodeASN1Integer(s)

        val derSignature = mutableListOf<Byte>()
        derSignature.add(0x30) // ASN.1 SEQUENCE tag
        derSignature.add((encodedR.size + encodedS.size).toByte())
        derSignature.addAll(encodedR.toList())
        derSignature.addAll(encodedS.toList())

        return derSignature.toByteArray()
    }
}