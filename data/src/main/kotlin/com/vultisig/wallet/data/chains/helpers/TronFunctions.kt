package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.stripHexPrefix
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigInteger
import java.security.MessageDigest

object TronFunctions {
    fun buildTrc20TransferParameters(recipientBaseHex: String, amount: BigInteger): String {
        val paddedAddressHex = recipientBaseHex.stripHexPrefix().drop(2).padStart(64, '0')
        val paddedAmountHex = amount.toString(16).padStart(64, '0')
        return paddedAddressHex + paddedAmountHex
    }

    /**
     * Decodes a base58check TRON address into the `0x41`-prefixed 21-byte hex the TVM expects in
     * calldata. Plain Kotlin rather than wallet-core's `Base58.decode` so fee estimation — which
     * runs on every amount keystroke — stays off JNI and remains reachable from JVM unit tests.
     */
    fun tronAddressToHex(address: String): String {
        require(address.isNotEmpty()) { "Tron address is empty" }

        var accumulator = BigInteger.ZERO
        for (character in address) {
            val digit = BASE58_ALPHABET.indexOf(character)
            require(digit >= 0) { "Tron address carries a non-base58 character" }
            accumulator = accumulator * BASE58 + digit.toBigInteger()
        }

        val magnitude =
            accumulator.toByteArray().let {
                // BigInteger prepends a sign byte for values whose high bit is set.
                if (it.size > 1 && it.first() == ZERO_BYTE) it.copyOfRange(1, it.size) else it
            }
        val leadingZeros = address.takeWhile { it == BASE58_ALPHABET.first() }.length
        val decoded = ByteArray(leadingZeros) + magnitude
        require(decoded.size == ADDRESS_BYTES + CHECKSUM_BYTES) {
            "Tron address decodes to ${decoded.size} bytes, expected " +
                "${ADDRESS_BYTES + CHECKSUM_BYTES}"
        }

        val payload = decoded.copyOfRange(0, ADDRESS_BYTES)
        val checksum = decoded.copyOfRange(ADDRESS_BYTES, decoded.size)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val expected = sha256.digest(sha256.digest(payload)).copyOfRange(0, CHECKSUM_BYTES)
        require(checksum.contentEquals(expected)) { "Tron address checksum does not match" }

        return Numeric.toHexString(payload)
    }

    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE58 = BigInteger.valueOf(58)
    private const val ZERO_BYTE: Byte = 0

    /** `41` prefix plus the 20-byte account. */
    private const val ADDRESS_BYTES = 21
    private const val CHECKSUM_BYTES = 4
}
