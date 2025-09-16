package com.vultisig.wallet.data.crypto

import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.utils.Numeric
import timber.log.Timber
import wallet.core.jni.Bech32
import wallet.core.jni.Hash
import kotlin.experimental.and

object CardanoUtils {

    fun createExtendedKey(spendingKeyHex: String, chainCodeHex: String): ByteArray {
        val spendingKeyData = Numeric.hexStringToByteArray(spendingKeyHex)
        val chainCodeData = Numeric.hexStringToByteArray(chainCodeHex)
        if (spendingKeyData.size != 32) {
            error("spending key must be 32 bytes, got ${spendingKeyData.size}")
        }
        if (chainCodeData.size != 32) {
            error("chain code must be 32 bytes, got ${chainCodeData.size}")
        }

        val extendedKeyData = ByteArray(128)
        System.arraycopy(
            spendingKeyData,
            0,
            extendedKeyData,
            0,
            32
        )
        System.arraycopy(
            spendingKeyData,
            0,
            extendedKeyData,
            32,
            32
        )
        System.arraycopy(
            chainCodeData,
            0,
            extendedKeyData,
            64,
            32
        )
        System.arraycopy(
            chainCodeData,
            0,
            extendedKeyData,
            96,
            32
        )

        if (extendedKeyData.size != 128) {
            error("extended key must be 128 bytes, got ${extendedKeyData.size}")
        }

        return extendedKeyData
    }


    fun createEnterpriseAddress(spendingKeyHex: String): String {
        val spendingKeyData = Numeric.hexStringToByteArray(spendingKeyHex)

        if (spendingKeyData.size != 32) {
            error("spending key must be 32 bytes, got ${spendingKeyData.size}")
        }

        // Use Blake2b hash with 28 bytes output size
        val hash = Hash.blake2b(
            spendingKeyData,
            28
        )

        // Create Enterprise address data: first byte (0x61) + 28-byte hash
        // 0x61 = (Kind_Enterprise << 4) + Network_Production = (6 << 4) + 1 = 0x61
        val addressData = ByteArray(29)
        addressData[0] = 0x61.toByte() // Enterprise address on Production network
        System.arraycopy(
            hash,
            0,
            addressData,
            1,
            28
        )

        // Convert to bech32 format with "addr" prefix
        return Bech32.encode(
            "addr",
            addressData
        )
    }


    fun calculateCardanoTransactionHash(transactionData: ByteArray): String {
        return try {
            val transactionBodyData = extractCardanoTransactionBody(transactionData)
            val txidHash = Utils.blake2bHash(transactionBodyData)
            txidHash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e("Error parsing Cardano CBOR: ${e.message}")
            val txidHash = Utils.blake2bHash(transactionData)
            val fallbackHash = txidHash.joinToString("") { "%02x".format(it) }
            Timber.w("Using fallback TX ID from COMPLETE transaction: $fallbackHash")
            fallbackHash
        }
    }

    @Throws(Exception::class)
    private fun extractCardanoTransactionBody(transactionData: ByteArray): ByteArray {
        val bytes = transactionData
        var index = 0

        if (index >= bytes.size) error("Invalid CBOR: empty data")

        val firstByte = bytes[index++]
        val majorType = (firstByte.toInt() shr 5) and 0x07
        if (majorType != 4) error("Invalid CBOR: expected array, got major type $majorType")

        val arrayInfo = (firstByte and 0x1F).toInt()
        val arrayLength = when {
            arrayInfo < 24 -> arrayInfo
            arrayInfo == 24 -> {
                if (index >= bytes.size) error("Invalid CBOR: array length truncated")
                bytes[index++].toInt() and 0xFF
            }

            else -> error("Unsupported CBOR array length encoding")
        }

        if (arrayLength < 2) error("Invalid Cardano transaction: array too short")

        val bodyStartIndex = index
        val bodyEndIndex = findEndOfCBORItem(
            bytes,
            bodyStartIndex
        )

        return bytes.copyOfRange(
            bodyStartIndex,
            bodyEndIndex
        )
    }

    @Throws(Exception::class)
    private fun findEndOfCBORItem(bytes: ByteArray, startIndex: Int): Int {
        var index = startIndex
        if (index >= bytes.size) error("CBOR parsing: index out of bounds")

        val firstByte = bytes[index++]
        val majorType = (firstByte.toInt() shr 5) and 0x07
        val additionalInfo = (firstByte and 0x1F).toInt()

        return when (majorType) {
            0, 1 -> { // Unsigned/Negative integers
                index + when (additionalInfo) {
                    in 0..23 -> 0
                    24 -> 1
                    25 -> 2
                    26 -> 4
                    27 -> 8
                    else -> error("Unsupported additional info for int")
                }
            }

            2, 3 -> { // Byte or text string
                val length = readCBORLength(
                    bytes,
                    index,
                    additionalInfo
                ).also { index += it.second }
                index + length.first
            }

            4 -> { // Array
                val length = readCBORLength(
                    bytes,
                    index,
                    additionalInfo
                ).also { index += it.second }
                repeat(length.first) {
                    index = findEndOfCBORItem(
                        bytes,
                        index
                    )
                }
                index
            }

            5 -> { // Map
                val length = readCBORLength(
                    bytes,
                    index,
                    additionalInfo
                ).also { index += it.second }
                repeat(length.first * 2) {
                    index = findEndOfCBORItem(
                        bytes,
                        index
                    )
                }
                index
            }

            7 -> { // Simple value or float
                index + when (additionalInfo) {
                    in 0..23 -> 0
                    24 -> 1
                    25 -> 2
                    26 -> 4
                    27 -> 8
                    else -> error("Unsupported CBOR simple value encoding")
                }
            }

            else -> error("Unsupported CBOR major type: $majorType")
        }
    }

    @Throws(Exception::class)
    private fun readCBORLength(
        bytes: ByteArray, startIndex: Int, additionalInfo: Int
    ): Pair<Int, Int> {
        var index = startIndex
        return when {
            additionalInfo < 24 -> Pair(
                additionalInfo,
                0
            )

            additionalInfo == 24 -> {
                if (index >= bytes.size) error("CBOR length truncated")
                Pair(
                    bytes[index++].toInt() and 0xFF,
                    1
                )
            }

            additionalInfo == 25 -> {
                if (index + 1 >= bytes.size) error("CBOR length truncated")
                val length =
                    ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
                Pair(
                    length,
                    2
                )
            }

            additionalInfo == 26 -> {
                if (index + 3 >= bytes.size) error("CBOR length truncated")
                val length =
                    ((bytes[index].toInt() and 0xFF) shl 24) or ((bytes[index + 1].toInt() and 0xFF) shl 16) or ((bytes[index + 2].toInt() and 0xFF) shl 8) or (bytes[index + 3].toInt() and 0xFF)
                Pair(
                    length,
                    4
                )
            }

            else -> error("Unsupported CBOR length encoding")
        }
    }
}