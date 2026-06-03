package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Chain

/**
 * Sanity-checks a Cosmos validator operator address (`terravaloper1…`) before the MPC ceremony
 * spends a signing round on a tx the chain will reject post-broadcast. Port of iOS
 * `ValidatorBech32Preflight.swift` (vultisig-ios PR #4432).
 *
 * Three layered checks (fail-closed at the first failure):
 * 1. Non-empty input.
 * 2. BIP-173 bech32 envelope — charset, no mixed case, separator present, valid checksum.
 * 3. Decoded HRP matches the chain's expected `terravaloper` prefix AND the payload is exactly 20
 *    bytes (Cosmos AccAddress / ValAddress = `ripemd160(sha256(pubkey))`).
 *
 * The bech32 verifier is implemented in pure Kotlin so the preflight is JVM-testable without the
 * Trust Wallet Core native library. The semantics are byte-equal with the iOS path (which delegates
 * to WalletCore's `AnyAddress.isValidBech32` + a 20-byte length guard).
 */
object ValidatorBech32Preflight {

    /** Cosmos AccAddress / ValAddress payload length. */
    private const val EXPECTED_PAYLOAD_LENGTH = 20

    sealed class ValidatorBech32Exception(message: String) : IllegalArgumentException(message) {
        object Empty : ValidatorBech32Exception("Validator address is empty")

        object BadEncoding :
            ValidatorBech32Exception("Validator address has invalid bech32 encoding")
    }

    /** Throws [ValidatorBech32Exception] when the address is empty or malformed. */
    fun validate(address: String, chain: Chain) {
        if (address.isEmpty()) throw ValidatorBech32Exception.Empty

        val expectedHrp = CosmosStakingConfig.valoperHrpFor(chain)
        val decoded = Bech32Decoder.decode(address) ?: throw ValidatorBech32Exception.BadEncoding

        if (decoded.hrp != expectedHrp) throw ValidatorBech32Exception.BadEncoding
        if (decoded.payload.size != EXPECTED_PAYLOAD_LENGTH) {
            throw ValidatorBech32Exception.BadEncoding
        }
    }
}

/**
 * Pure-Kotlin BIP-173 bech32 decoder. Validates the charset, mixed-case rule, separator, checksum,
 * and 5→8 bit payload conversion; returns `null` on any failure.
 *
 * Reference: https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki
 */
internal object Bech32Decoder {

    data class Decoded(val hrp: String, val payload: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Decoded) return false
            return hrp == other.hrp && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int = 31 * hrp.hashCode() + payload.contentHashCode()
    }

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    private const val CHECKSUM_LENGTH = 6

    fun decode(input: String): Decoded? {
        // BIP-173: mixed case is forbidden. Reject before lowercasing.
        if (input.any { it.isUpperCase() } && input.any { it.isLowerCase() }) return null
        val lower = input.lowercase()

        val separator = lower.lastIndexOf('1')
        if (separator < 1 || separator + CHECKSUM_LENGTH + 1 > lower.length) return null

        val hrp = lower.substring(0, separator)
        if (hrp.any { it.code < 33 || it.code > 126 }) return null

        val dataPart = lower.substring(separator + 1)
        val data5Bit = IntArray(dataPart.length)
        for ((index, c) in dataPart.withIndex()) {
            val symbol = CHARSET.indexOf(c)
            if (symbol < 0) return null
            data5Bit[index] = symbol
        }

        if (!verifyChecksum(hrp, data5Bit)) return null

        val payload5Bit = data5Bit.copyOfRange(0, data5Bit.size - CHECKSUM_LENGTH)
        val payload8Bit =
            convertBits(payload5Bit, fromBits = 5, toBits = 8, pad = false) ?: return null

        return Decoded(hrp = hrp, payload = payload8Bit)
    }

    private fun verifyChecksum(hrp: String, data: IntArray): Boolean {
        val values = hrpExpand(hrp) + data.toList()
        return polymod(values) == 1
    }

    private fun hrpExpand(hrp: String): List<Int> {
        val bytes = hrp.map { it.code }
        return bytes.map { it ushr 5 } + 0 + bytes.map { it and 0x1f }
    }

    private fun polymod(values: List<Int>): Int {
        var chk = 1
        for (value in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor value
            for (index in 0 until 5) {
                if (((top ushr index) and 1) == 1) chk = chk xor GENERATOR[index]
            }
        }
        return chk
    }

    /**
     * Reverse of the 8→5 packing that the encoder applies. `pad = false` is the verifier path:
     * partial-byte input is rejected (any leftover bits or a non-zero pad indicates corruption).
     */
    private fun convertBits(data: IntArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray? {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Byte>()
        val maxv = (1 shl toBits) - 1
        for (value in data) {
            if (value < 0 || (value ushr fromBits) != 0) return null
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc ushr bits) and maxv).toByte())
            }
        }
        if (pad) {
            if (bits > 0) result.add(((acc shl (toBits - bits)) and maxv).toByte())
        } else if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) {
            return null
        }
        return result.toByteArray()
    }
}
