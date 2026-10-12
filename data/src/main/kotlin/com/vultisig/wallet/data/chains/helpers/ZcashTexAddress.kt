package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.crypto.Base58Codec
import java.security.MessageDigest

/**
 * THORChain's ZEC inbound vault is a ZIP-320 TEX address (`tex1…`: bech32m over the 20-byte P2PKH
 * hash). WalletCore only parses `t1…`, so the equivalent transparent address is paid instead — same
 * hash, same output script. Mirrors iOS `UTXOChainsHelper.zcashTransparentAddress(fromTex:)`.
 */
internal object ZcashTexAddress {
    private const val HRP_PREFIX = "tex1"
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BECH32M_CONST = 0x2bc830a3
    private val GENERATORS = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    private val T_ADDR_PREFIX = byteArrayOf(0x1c, 0xb8.toByte())

    /** The transparent `t1…` form of [address], or [address] unchanged if it is not a valid TEX. */
    fun toTransparent(address: String): String {
        val lower = address.lowercase()
        if (!lower.startsWith(HRP_PREFIX) || (address != lower && address != address.uppercase())) {
            return address
        }
        val values = lower.drop(HRP_PREFIX.length).map { CHARSET.indexOf(it) }
        if (values.any { it < 0 } || values.size <= 6) return address
        // hrp "tex" expanded: high bits, 0, low bits.
        val expanded = listOf(3, 3, 3, 0, 20, 5, 24) + values
        var chk = 1
        for (v in expanded) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in GENERATORS.indices) if ((top ushr i) and 1 == 1) chk = chk xor GENERATORS[i]
        }
        if (chk != BECH32M_CONST) return address

        var acc = 0
        var bits = 0
        val hash = ArrayList<Byte>()
        for (v in values.dropLast(6)) {
            acc = (acc shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                hash += ((acc shr bits) and 0xff).toByte()
                acc = acc and ((1 shl bits) - 1)
            }
        }
        // Exactly the 20-byte hash with clean padding.
        if (hash.size != 20 || bits >= 5 || acc != 0) return address
        val payload = T_ADDR_PREFIX + hash.toByteArray()
        val sha = MessageDigest.getInstance("SHA-256")
        val checksum = sha.digest(sha.digest(payload)).copyOf(4)
        return Base58Codec.encode(payload + checksum)
    }
}
