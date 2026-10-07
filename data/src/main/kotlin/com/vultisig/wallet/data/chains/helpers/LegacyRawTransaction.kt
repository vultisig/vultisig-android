package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import java.security.MessageDigest

/**
 * The outputs of a serialized legacy (non-segwit) Bitcoin-family transaction, and its txid.
 *
 * Enough to check a UTXO against the transaction that created it: the txid is the double SHA-256
 * of the bytes themselves, so whoever served them cannot alter an output without the txid ceasing
 * to match the outpoint that names it. Dash special transactions (DIP-2) carry their type in the
 * version field and append their payload after the lock time, so inputs and outputs read the same.
 */
internal class LegacyRawTransaction private constructor(val txid: String, val outputs: List<Output>) {

    class Output(val value: Long, val script: ByteArray)

    companion object {
        /** Parses [hex], or returns null when it is not a well-formed legacy transaction. */
        fun parseOrNull(hex: String): LegacyRawTransaction? =
            runCatching { parse(hex.trim().removePrefix("0x").hexToByteArray()) }.getOrNull()

        private fun parse(bytes: ByteArray): LegacyRawTransaction {
            val reader = Reader(bytes)
            reader.skip(4) // version (and the DIP-2 type on Dash)
            val inputCount = reader.varInt()
            // A zero input count is the segwit marker; DOGE and DASH have no segwit.
            require(inputCount > 0) { "No inputs, or a segwit transaction" }
            repeat(inputCount.toInt()) {
                reader.skip(32 + 4) // previous outpoint
                reader.skip(reader.varInt().toInt()) // scriptSig
                reader.skip(4) // sequence
            }
            val outputs =
                List(reader.varInt().toInt()) {
                    val value = reader.uint64Le()
                    Output(value = value, script = reader.take(reader.varInt().toInt()))
                }
            reader.skip(4) // lock time; any Dash special payload follows and is not read
            val digest = MessageDigest.getInstance("SHA-256")
            val txid = digest.digest(digest.digest(bytes)).reversedArray().toHexString()
            return LegacyRawTransaction(txid = txid, outputs = outputs)
        }
    }

    private class Reader(private val bytes: ByteArray) {
        private var position = 0

        fun take(count: Int): ByteArray {
            require(count >= 0 && position + count <= bytes.size) { "Truncated transaction" }
            return bytes.copyOfRange(position, position + count).also { position += count }
        }

        fun skip(count: Int) {
            take(count)
        }

        fun uint64Le(): Long {
            val value = BigInteger(1, take(8).reversedArray())
            require(value.bitLength() < 64) { "Output value out of range" }
            return value.toLong()
        }

        fun varInt(): Long {
            val first = take(1)[0].toInt() and 0xff
            val width =
                when (first) {
                    0xfd -> 2
                    0xfe -> 4
                    0xff -> 8
                    else -> return first.toLong()
                }
            val value = BigInteger(1, take(width).reversedArray())
            require(value.bitLength() < 32) { "Count out of range" }
            return value.toLong()
        }
    }
}
