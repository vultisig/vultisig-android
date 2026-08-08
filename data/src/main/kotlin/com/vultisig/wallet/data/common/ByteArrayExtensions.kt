package com.vultisig.wallet.data.common

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.jcajce.provider.digest.Keccak

fun ByteArray.toKeccak256(): String {
    return Numeric.toHexString(this.toKeccak256ByteArray())
}

fun ByteArray.toKeccak256ByteArray(): ByteArray {
    val digest = Keccak.Digest256()
    return digest.digest(this)
}

fun ByteArray.toSha256ByteArray(): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(this)
}

fun ByteArray.toSha512ByteArray(): ByteArray {
    return MessageDigest.getInstance("SHA-512").digest(this)
}

/**
 * Sui Wallet Standard `signPersonalMessage` / `signMessage` digest: `blake2b_256(intent ||
 * bcs(message))`.
 *
 * The intent prefix is `[scope, version, appId]` = `[PersonalMessage(3), V0(0), Sui(0)]`, and the
 * message is BCS-encoded as `vector<u8>` — a ULEB128 length prefix followed by the raw bytes.
 * Mirrors `@mysten/sui`'s `signPersonalMessage` (`messageWithIntent('PersonalMessage',
 * bcs.byteVector().serialize(bytes))` then `blake2b(_, dkLen = 32)`) and the vultisig-windows
 * initiator's `getSuiPersonalMessageDigest`. Signing the raw bytes instead — which every other
 * EdDSA chain correctly does — diverges from the initiator's digest, so the MPC round never
 * converges.
 *
 * Note this is a different intent scope from a built Sui transaction (PTB), which uses
 * `TransactionData(0)` and no BCS wrap; see `SwapKitSuiSigner.digest`.
 */
fun ByteArray.toSuiPersonalMessageDigest(): ByteArray {
    val intentMessage = SUI_PERSONAL_MESSAGE_INTENT + ulebEncode(size) + this
    val blake = Blake2bDigest(SUI_DIGEST_SIZE_BITS)
    blake.update(intentMessage, 0, intentMessage.size)
    return ByteArray(blake.digestSize).also { blake.doFinal(it, 0) }
}

/** BCS ULEB128 length prefix — 7 payload bits per byte, high bit set while more bytes follow. */
private fun ulebEncode(value: Int): ByteArray {
    val out = ArrayList<Byte>()
    var remaining = value
    while (remaining >= 0x80) {
        out.add(((remaining and 0x7F) or 0x80).toByte())
        remaining = remaining ushr 7
    }
    out.add((remaining and 0x7F).toByte())
    return out.toByteArray()
}

private val SUI_PERSONAL_MESSAGE_INTENT = byteArrayOf(0x03, 0x00, 0x00)

private const val SUI_DIGEST_SIZE_BITS = 256

fun ByteArray.toByteString(): ByteString {
    return ByteString.copyFrom(this)
}

/** Lowercase hex string of the bytes, with no `0x` prefix. */
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** `0x`-prefixed hex string, the shape EVM JSON-RPC expects for a call's `data` field. */
fun ByteArray.asCallData(): String = Numeric.toHexString(this)

/** Base64 without the line breaks `Base64.getMimeEncoder()` would insert. */
fun ByteArray.base64NoWrap(): String = Base64.getEncoder().encodeToString(this)

/** Reads the bytes as an unsigned little-endian integer, as BCS encodes `u64` / `u128`. */
fun ByteArray.toLittleEndianBigInteger(): BigInteger {
    var result = BigInteger.ZERO
    for (i in indices.reversed()) {
        result = result.shiftLeft(8).or(BigInteger.valueOf(this[i].toLong() and 0xFF))
    }
    return result
}
