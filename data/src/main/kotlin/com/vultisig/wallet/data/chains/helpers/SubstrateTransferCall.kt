package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import timber.log.Timber

/** The recipient and value of a Balances transfer read out of a dApp's SCALE call bytes. */
data class SubstrateTransferCall(val destination: ByteArray, val amount: BigInteger)

/**
 * What a display surface learned about a dApp's call bytes — see
 * [SubstrateTransferCallReader.readForDisplay].
 */
sealed interface SubstrateCallReading {
    data class Transfer(val call: SubstrateTransferCall) : SubstrateCallReading

    /** A call outside the two Balances transfers; there is no amount to show. */
    data object NotATransfer : SubstrateCallReading

    /** Balances-transfer-shaped bytes the strict reader refused — shown raw, under a warning. */
    data object Unreadable : SubstrateCallReading
}

/**
 * Reads the recipient and value of a `Balances.transfer_allow_death` / `transfer_keep_alive` out of
 * the call a dApp asked the vault to sign — a port of the extension's `decodeTransferCall.ts`, so
 * both sides of a ceremony review the same number.
 *
 * Returns null for any other call: a staking or subnet call carries no comparable scalar, and a
 * number on Verify that the signed bytes do not mean is exactly what this exists to prevent. Throws
 * when the bytes claim to be a transfer but do not decode, and when they are too short to even name
 * a pallet and call — no runtime decodes that, so the caller fails closed instead of showing a
 * guess, exactly as the extension's reader does.
 */
object SubstrateTransferCallReader {

    /** `pallet_balances` sits at index 5 in both the Polkadot relay and Bittensor runtimes. */
    private const val BALANCES_PALLET = 5

    /** `transfer_allow_death` and `transfer_keep_alive`: the two calls shaped recipient + value. */
    private val transferCallIndices = setOf(0, 3)

    private const val MULTI_ADDRESS_ID = 0x00
    private const val ACCOUNT_ID_LENGTH = 32

    // Canonical SCALE gives every value exactly one spelling; `parity-scale-codec` refuses any
    // other
    // with "out of range decoding Compact<T>", so a call carrying one never decodes on chain.
    private val twoByteCompactMinimum = BigInteger.valueOf(64)
    private val fourByteCompactMinimum = BigInteger.valueOf(16_384)
    private val bigIntegerCompactMinimum = BigInteger.valueOf(1_073_741_824)

    /** Balance is `u128` on both runtimes; a wider compact is not a balance either side decodes. */
    private const val MAX_BIG_INTEGER_COMPACT_BYTES = 16

    /**
     * [read] for Verify and the done screen: the strict reader's refusal becomes
     * [SubstrateCallReading.Unreadable] instead of an exception, so a payload whose transfer cannot
     * be read (a `MultiAddress::Address32` recipient, say, or a malformed compact) still shows its
     * raw signer payload and call data under a warning rather than failing the whole join. Signing
     * never goes through here — it signs the call bytes as given and decodes nothing.
     */
    fun readForDisplay(method: ByteArray): SubstrateCallReading =
        try {
            read(method)?.let { SubstrateCallReading.Transfer(it) }
                ?: SubstrateCallReading.NotATransfer
        } catch (e: IllegalStateException) {
            Timber.w(e, "Substrate call could not be read as a transfer; showing it raw")
            SubstrateCallReading.Unreadable
        }

    fun read(method: ByteArray): SubstrateTransferCall? {
        val reader = ByteReader(method)
        val palletIndex = reader.u8()
        val callIndex = reader.u8()
        if (palletIndex != BALANCES_PALLET || callIndex !in transferCallIndices) return null

        val addressVariant = reader.u8()
        check(addressVariant == MULTI_ADDRESS_ID) {
            "Unsupported Substrate MultiAddress variant $addressVariant"
        }
        val destination = reader.bytes(ACCOUNT_ID_LENGTH)
        val amount = reader.compact()
        // A transfer is exactly dest plus value. Anything still unread means the call is not the
        // one
        // these indices describe, so the amount just decoded is not the amount that gets signed.
        check(reader.isExhausted) { "Substrate transfer call has trailing bytes" }
        return SubstrateTransferCall(destination, amount)
    }

    private class ByteReader(private val bytes: ByteArray) {
        private var offset = 0

        val isExhausted: Boolean
            get() = offset == bytes.size

        fun u8(): Int {
            check(offset < bytes.size) { "Substrate call ended mid-field" }
            return bytes[offset++].toInt() and 0xFF
        }

        fun bytes(length: Int): ByteArray {
            check(offset + length <= bytes.size) { "Substrate call ended mid-field" }
            return bytes.copyOfRange(offset, offset + length).also { offset += length }
        }

        fun compact(): BigInteger {
            val first = u8()
            return when (first and 0b11) {
                0b00 -> BigInteger.valueOf((first ushr 2).toLong())
                0b01 -> {
                    val raw = first.toLong() or (u8().toLong() shl 8)
                    canonical(BigInteger.valueOf(raw ushr 2), twoByteCompactMinimum)
                }
                0b10 -> {
                    var raw = first.toLong()
                    for (index in 1 until 4) raw = raw or (u8().toLong() shl (8 * index))
                    canonical(BigInteger.valueOf(raw ushr 2), fourByteCompactMinimum)
                }
                else -> {
                    val length = (first ushr 2) + 4
                    check(length <= MAX_BIG_INTEGER_COMPACT_BYTES) {
                        "Substrate call compact integer is wider than a balance"
                    }
                    var value = BigInteger.ZERO
                    var mostSignificantByte = 0
                    for (index in 0 until length) {
                        mostSignificantByte = u8()
                        value =
                            value or BigInteger.valueOf(mostSignificantByte.toLong()).shl(8 * index)
                    }
                    check(mostSignificantByte != 0) {
                        "Substrate call uses a non-canonical compact integer"
                    }
                    canonical(value, bigIntegerCompactMinimum)
                }
            }
        }

        private fun canonical(value: BigInteger, minimum: BigInteger): BigInteger {
            check(value >= minimum) { "Substrate call uses a non-canonical compact integer" }
            return value
        }
    }
}
