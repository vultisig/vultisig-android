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
                Regex(
                        "^(bc1[ac-hj-np-z02-9]{11,71}|[13][$BASE58_CHARS]{25,34})$",
                        RegexOption.IGNORE_CASE,
                    )
                    .matches(a)
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
                Regex(
                        "^(ltc1[ac-hj-np-z02-9]{11,71}|[LM3][$BASE58_CHARS]{25,34})$",
                        RegexOption.IGNORE_CASE,
                    )
                    .matches(a)
            },
        Chain.Zcash to { a -> Regex("^t[13][$BASE58_CHARS]{33}$").matches(a) },
        Chain.Solana to ::isSolanaAddress,
        Chain.GaiaChain to { a -> isBech32Address(a, "cosmos") },
        Chain.Kujira to { a -> isBech32Address(a, "kujira") },
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

/**
 * Decode a bech32 string and return its human-readable prefix, or null if the string is not a
 * checksum-valid bech32 (constant 1). Only the HRP is needed here; the data payload is discarded.
 */
private fun bech32DecodeHrp(input: String): String? {
    if (input.length < 8 || input.length > 90) return null
    if (input != input.lowercase() && input != input.uppercase()) return null
    val lower = input.lowercase()
    val separator = lower.lastIndexOf('1')
    if (separator < 1 || separator + 7 > lower.length) return null

    val hrp = lower.substring(0, separator)
    val dataPart = lower.substring(separator + 1)
    val data = IntArray(dataPart.length)
    for (i in dataPart.indices) {
        val idx = BECH32_CHARSET.indexOf(dataPart[i])
        if (idx < 0) return null
        data[i] = idx
    }
    if (!bech32VerifyChecksum(hrp, data)) return null
    return hrp
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

private fun bech32VerifyChecksum(hrp: String, data: IntArray): Boolean {
    val values = bech32HrpExpand(hrp) + data
    return bech32Polymod(values) == 1
}
