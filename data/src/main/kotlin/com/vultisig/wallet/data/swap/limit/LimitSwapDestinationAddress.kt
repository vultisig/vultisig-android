package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger

/**
 * Destination-address validation for THORChain limit orders, ported from the Vultisig SDK's
 * `limitSwapMemo` validators.
 *
 * The destination is the address the filled order pays out to — normally the user's own address on
 * the target chain. It is validated defensively before it is baked into a signed memo: the memo
 * *is* the order, so a malformed destination must fail here rather than strand funds on-chain. Kept
 * as pure Kotlin (no wallet-core JNI) so the whole memo path is unit-testable on any JVM.
 */
private const val BASE58_CHARS = "1-9A-HJ-NP-Za-km-z"

/**
 * Reject anything that cannot sit safely inside a `:`/`/`-delimited THORChain memo segment: empty
 * values, embedded separators, whitespace, or non-printable / non-ASCII bytes.
 */
internal fun assertMemoSegmentSafe(value: String, fieldName: String) {
    require(value.isNotEmpty()) { "$fieldName must be a non-empty string" }
    require(!value.contains(':') && !value.contains('/')) {
        "$fieldName must not contain memo separators \":\" or \"/\""
    }
    require(!value.any { it.isWhitespace() }) { "$fieldName must not contain whitespace" }
    require(value.all { it.code in 0x21..0x7E }) {
        "$fieldName must contain printable ASCII characters only"
    }
}

private fun isEvmAddress(address: String): Boolean = Regex("^0x[0-9a-fA-F]{40}$").matches(address)

private val limitSwapDestinationValidators: Map<Chain, (String) -> Boolean> =
    mapOf(
        Chain.Arbitrum to ::isEvmAddress,
        Chain.Avalanche to ::isEvmAddress,
        Chain.Base to ::isEvmAddress,
        Chain.BscChain to ::isEvmAddress,
        Chain.Ethereum to ::isEvmAddress,
        Chain.Bitcoin to
            { a ->
                isSegwitAddress(a, "bc") ||
                    Regex("^[13][$BASE58_CHARS]{25,34}$", RegexOption.IGNORE_CASE).matches(a)
            },
        Chain.BitcoinCash to
            { a ->
                Regex("^([qp][0-9a-z]{41}|[13][$BASE58_CHARS]{25,34})$", RegexOption.IGNORE_CASE)
                    .matches(a)
            },
        Chain.Dash to { a -> Regex("^X[$BASE58_CHARS]{33}$").matches(a) },
        Chain.Dogecoin to { a -> Regex("^D[5-9A-HJ-NP-U][$BASE58_CHARS]{32}$").matches(a) },
        Chain.Litecoin to
            { a ->
                isSegwitAddress(a, "ltc") ||
                    Regex("^[LM3][$BASE58_CHARS]{25,34}$", RegexOption.IGNORE_CASE).matches(a)
            },
        Chain.Zcash to { a -> Regex("^t[13][$BASE58_CHARS]{33}$").matches(a) },
        Chain.Solana to ::isSolanaAddress,
        Chain.GaiaChain to { a -> isBech32Address(a, "cosmos") },
        Chain.ThorChain to { a -> isBech32Address(a, "thor") },
        Chain.Noble to { a -> isBech32Address(a, "noble") },
        Chain.Ripple to { a -> Regex("^r[$BASE58_CHARS]{24,34}$").matches(a) },
        Chain.Tron to { a -> Regex("^T[$BASE58_CHARS]{33}$").matches(a) },
    )

/**
 * Assert [address] is a valid destination for a limit order paying out to [targetChain]. Throws
 * when the chain has no validator (i.e. it is not a supported limit-swap destination) or the
 * address is malformed for it.
 */
internal fun assertValidLimitSwapDestinationAddress(targetChain: Chain, address: String) {
    val validator =
        requireNotNull(limitSwapDestinationValidators[targetChain]) {
            "target_asset chain $targetChain is not supported for limit swap destinations"
        }
    require(validator(address)) { "dest_addr is not a valid $targetChain address" }
}

private fun isSolanaAddress(address: String): Boolean {
    if (!Regex("^[$BASE58_CHARS]{32,44}$").matches(address)) return false
    val decoded = base58Decode(address) ?: return false
    return decoded.size == 32
}

private fun isBech32Address(address: String, expectedPrefix: String): Boolean {
    val hrp = bech32DecodeHrp(address) ?: return false
    return hrp == expectedPrefix
}

/** Big-integer Base58 decode (Bitcoin alphabet). Returns null on any non-alphabet character. */
private fun base58Decode(input: String): ByteArray? {
    val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    val base = BigInteger.valueOf(58)
    var num = BigInteger.ZERO
    for (c in input) {
        val digit = alphabet.indexOf(c)
        if (digit < 0) return null
        num = num.multiply(base).add(BigInteger.valueOf(digit.toLong()))
    }
    var bytes = num.toByteArray()
    // Drop the sign byte BigInteger prepends for values whose high bit is set.
    if (bytes.size > 1 && bytes[0].toInt() == 0) {
        bytes = bytes.copyOfRange(1, bytes.size)
    }
    val leadingZeros = input.takeWhile { it == '1' }.length
    return ByteArray(leadingZeros) + bytes
}

private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

/** Checksum constants: bech32 per BIP-173, bech32m per BIP-350. */
private const val BECH32_CONST = 1
private const val BECH32M_CONST = 0x2bc830a3

private const val CHECKSUM_LENGTH = 6

/**
 * Parse a bech32 string into its human-readable prefix and 5-bit data payload, enforcing the
 * encoding's structural rules (length bounds, case uniformity, charset). The checksum is *not*
 * verified here: segwit picks its constant from the witness version, so that is left to callers.
 */
private fun bech32Split(input: String): Pair<String, IntArray>? {
    if (input.length < 8 || input.length > 90) return null
    if (input != input.lowercase() && input != input.uppercase()) return null
    val lower = input.lowercase()
    val separator = lower.lastIndexOf('1')
    if (separator < 1 || separator + 1 + CHECKSUM_LENGTH > lower.length) return null

    val hrp = lower.substring(0, separator)
    val dataPart = lower.substring(separator + 1)
    val data = IntArray(dataPart.length)
    for (i in dataPart.indices) {
        val idx = BECH32_CHARSET.indexOf(dataPart[i])
        if (idx < 0) return null
        data[i] = idx
    }
    return hrp to data
}

/**
 * Decode a bech32 string and return its human-readable prefix, or null if the string is not a
 * checksum-valid bech32 (constant 1). Only the HRP is needed here; the data payload is discarded.
 */
private fun bech32DecodeHrp(input: String): String? {
    val (hrp, data) = bech32Split(input) ?: return null
    if (!bech32VerifyChecksum(hrp, data, BECH32_CONST)) return null
    return hrp
}

/**
 * Validate a segwit payout address under [expectedHrp] per BIP-173/BIP-350. Witness v0 carries a
 * bech32 checksum while v1+ (taproot and anything later) carries a bech32m one, so a single
 * constant would silently reject every `bc1p…` destination. The witness program itself is decoded
 * too, which rejects the truncated, over-padded and non-zero-padding forms a shape-only regex lets
 * through.
 */
private fun isSegwitAddress(address: String, expectedHrp: String): Boolean {
    val (hrp, data) = bech32Split(address) ?: return false
    if (hrp != expectedHrp) return false
    if (data.size < 1 + CHECKSUM_LENGTH) return false

    val witnessVersion = data[0]
    if (witnessVersion > 16) return false
    val checksumConst = if (witnessVersion == 0) BECH32_CONST else BECH32M_CONST
    if (!bech32VerifyChecksum(hrp, data, checksumConst)) return false

    val program = convertBits(data.copyOfRange(1, data.size - CHECKSUM_LENGTH)) ?: return false
    if (program.size < 2 || program.size > 40) return false
    // v0 is defined only for P2WPKH (20 bytes) and P2WSH (32 bytes).
    if (witnessVersion == 0 && program.size != 20 && program.size != 32) return false
    return true
}

/** Regroup 5-bit bech32 data into bytes, rejecting non-zero or over-wide trailing padding. */
private fun convertBits(data: IntArray): ByteArray? {
    var accumulator = 0
    var bits = 0
    val out = ArrayList<Byte>(data.size * 5 / 8)
    for (value in data) {
        accumulator = (accumulator shl 5) or value
        bits += 5
        while (bits >= 8) {
            bits -= 8
            out.add(((accumulator shr bits) and 0xff).toByte())
        }
    }
    if (bits >= 5 || (accumulator shl (8 - bits)) and 0xff != 0) return null
    return out.toByteArray()
}

private fun bech32HrpExpand(hrp: String): IntArray {
    val result = IntArray(hrp.length * 2 + 1)
    for (i in hrp.indices) {
        result[i] = hrp[i].code shr 5
        result[hrp.length + 1 + i] = hrp[i].code and 31
    }
    result[hrp.length] = 0
    return result
}

private fun bech32Polymod(values: IntArray): Int {
    val generators = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
    var chk = 1
    for (value in values) {
        val top = chk shr 25
        chk = (chk and 0x1ffffff) shl 5 xor value
        for (i in 0 until 5) {
            if ((top shr i) and 1 == 1) chk = chk xor generators[i]
        }
    }
    return chk
}

private fun bech32VerifyChecksum(hrp: String, data: IntArray, checksumConst: Int): Boolean {
    val values = bech32HrpExpand(hrp) + data
    return bech32Polymod(values) == checksumConst
}
