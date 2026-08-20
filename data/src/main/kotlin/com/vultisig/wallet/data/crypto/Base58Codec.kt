package com.vultisig.wallet.data.crypto

import java.math.BigInteger

/**
 * Base58 over the Bitcoin alphabet, without a checksum — the encoding Solana addresses use.
 *
 * Pure Kotlin rather than WalletCore's `Base58`, so that the rules built on it stay unit-testable
 * off-device: an address derivation that needs JNI can only be exercised in an instrumented test,
 * and the checks that compare derived addresses are the ones most worth running everywhere.
 */
internal object Base58Codec {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    private val BASE = BigInteger.valueOf(58)

    /**
     * The bytes [input] encodes, or null on any character outside the alphabet.
     *
     * Leading `1`s are the encoding of leading zero bytes and are restored as such, which matters
     * for fixed-width keys: dropping them would shorten a 32-byte pubkey.
     */
    fun decode(input: String): ByteArray? {
        var number = BigInteger.ZERO
        for (character in input) {
            val digit = ALPHABET.indexOf(character)
            if (digit < 0) return null
            number = number.multiply(BASE).add(BigInteger.valueOf(digit.toLong()))
        }
        var bytes = number.toByteArray()
        // Drop the sign byte BigInteger prepends for values whose high bit is set.
        if (bytes.size > 1 && bytes[0].toInt() == 0) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        if (bytes.size == 1 && bytes[0].toInt() == 0) {
            bytes = ByteArray(0)
        }
        val leadingZeros = input.takeWhile { it == '1' }.length
        return ByteArray(leadingZeros) + bytes
    }

    /** The Base58 form of [bytes], with each leading zero byte written as a `1`. */
    fun encode(bytes: ByteArray): String {
        val leadingZeros = bytes.takeWhile { it.toInt() == 0 }.size
        var number = BigInteger(1, bytes)
        val builder = StringBuilder()
        while (number.signum() > 0) {
            val (quotient, remainder) = number.divideAndRemainder(BASE)
            builder.append(ALPHABET[remainder.toInt()])
            number = quotient
        }
        repeat(leadingZeros) { builder.append(ALPHABET[0]) }
        return builder.reverse().toString()
    }
}
