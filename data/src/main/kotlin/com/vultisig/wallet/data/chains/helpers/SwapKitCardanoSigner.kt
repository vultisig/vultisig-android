@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.tss.getSignature
import org.bouncycastle.crypto.digests.Blake2bDigest
import tss.KeysignResponse
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

internal class SwapKitCardanoSignerException(message: String) : Exception(message)

/**
 * Signs SwapKit's pre-built Cardano CBOR transaction. Ported from iOS' `SwapKitCardanoSigner` +
 * `CardanoSignedTxBuilder`. SwapKit performs UTXO selection, change splitting, and fee computation
 * server-side and returns the unsigned tx as a hex-encoded CBOR envelope (routed via NEAR Intents).
 * We sign the body bytes verbatim — never through WalletCore's `Cardano.SigningInput` (which can't
 * take a pre-built envelope) — so the broadcast tx_id matches the one the route tracks by.
 *
 * Cardano signing model (Shelley-era):
 * ```
 *   tx_envelope = [
 *       transaction_body,        // CBOR map — the bytes we hash
 *       transaction_witness_set, // unsigned: empty (a0); we splice the vkey witness here
 *       is_valid,                // true (f5)
 *       auxiliary_data           // null (f6)
 *   ]
 *   tx_id  = blake2b_256(cbor(transaction_body))
 *   digest = tx_id                              // the message MPC's Ed25519 signs
 *   witness_set = { 0: [[vkey_32, signature_64]] }
 * ```
 *
 * The digest path uses Bouncy Castle Blake2b-256 (JNI-free, unit-testable, identical to
 * WalletCore's `Hash.blake2b(_, 32)`); the signing path adds a WalletCore Ed25519 verify before
 * assembly. The body / is_valid / auxiliary_data bytes are re-emitted byte-for-byte — re-encoding
 * could change CBOR integer widths or map ordering and invalidate the signature. A tiny
 * definite-length CBOR walker measures the four envelope items; Cardano envelopes are small and
 * well-formed by construction, so we don't pull in a CBOR library.
 *
 * The 32-byte vkey written into the witness is the vault's Ed25519 public key — the same key
 * Vultisig derives the Cardano enterprise address from (`CardanoUtils.createEnterpriseAddress`), so
 * its blake2b-224 hash matches the payment credential SwapKit built the tx against.
 */
internal class SwapKitCardanoSigner(private val vaultHexPublicKey: String) {

    /**
     * Cardano signing digest = `blake2b_256(cbor(transaction_body))`. Cardano signs the body bytes
     * only (not the whole envelope), so we slice item 0 verbatim and hash. A single-element list: a
     * Cardano transaction signs one hash regardless of input count; hex-encoded to match the
     * keysign message-hash convention.
     */
    fun getPreSignedImageHash(cborBytes: ByteArray): List<String> =
        listOf(digest(cborBytes).toHexString())

    /**
     * Assemble the signed broadcast envelope: keep items 0/2/3 verbatim and replace item 1
     * (witness_set) with `{ 0: [[vkey, sig]] }`. [rawTransaction][SignedTransactionResult] is the
     * broadcast hex consumed by the Cardano submit endpoint;
     * [transactionHash][SignedTransactionResult] is the Cardano tx_id (== blake2b_256(body)).
     */
    fun getSignedTransaction(
        cborBytes: ByteArray,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val pubKeyData = vaultHexPublicKey.hexToByteArray()
        val publicKey = PublicKey(pubKeyData, PublicKeyType.ED25519)

        val parsed = parseEnvelope(cborBytes)
        val digest = blake2b256(parsed.body)
        val key = digest.toHexString()
        val signature =
            signatures[key]?.getSignature()
                ?: throw SwapKitCardanoSignerException(
                    "Missing signature for Cardano digest ${key.take(16)}…"
                )
        if (!publicKey.verify(signature, digest)) {
            throw SwapKitCardanoSignerException("SwapKit Cardano signature verification failed")
        }

        val assembled = assembleSignedTransaction(parsed, pubKeyData, signature)
        return SignedTransactionResult(
            rawTransaction = assembled.toHexString(),
            transactionHash = key,
        )
    }

    /**
     * Blake2b-256 of `cbor(transaction_body)`. Exposed (internal) so a unit test can pin the digest
     * of a known-good envelope without JNI.
     */
    internal fun digest(cborBytes: ByteArray): ByteArray = blake2b256(parseEnvelope(cborBytes).body)

    /**
     * Assemble the broadcast-format envelope from raw signature + vkey bytes. Exposed (internal) so
     * a unit test can verify the witness framing without going through MPC.
     */
    internal fun assembleSignedTransaction(
        cborBytes: ByteArray,
        signature: ByteArray,
        verificationKey: ByteArray,
    ): ByteArray = assembleSignedTransaction(parseEnvelope(cborBytes), verificationKey, signature)

    private fun assembleSignedTransaction(
        parsed: ParsedEnvelope,
        publicKey: ByteArray,
        signature: ByteArray,
    ): ByteArray {
        require(publicKey.size == PUBLIC_KEY_LENGTH) {
            "Cardano vkey must be $PUBLIC_KEY_LENGTH bytes, got ${publicKey.size}"
        }
        require(signature.size == SIGNATURE_LENGTH) {
            "Cardano signature must be $SIGNATURE_LENGTH bytes, got ${signature.size}"
        }

        // witness_set = { 0: [ [vkey, sig] ] }  →  a1 00 81 82 <bytes(vkey)> <bytes(sig)>
        val witness =
            byteArrayOf(0xA1.toByte(), 0x00, 0x81.toByte(), 0x82.toByte()) +
                cborBytes(publicKey) +
                cborBytes(signature)

        // tx_envelope = array(4) [body, witness_set, is_valid, auxiliary_data]
        return byteArrayOf(0x84.toByte()) + parsed.body + witness + parsed.isValid + parsed.auxData
    }

    /**
     * Byte ranges of the four top-level envelope items we re-emit during assembly. The body is also
     * the input to the signing digest.
     */
    private class ParsedEnvelope(
        val body: ByteArray,
        val isValid: ByteArray,
        val auxData: ByteArray,
    )

    private fun parseEnvelope(data: ByteArray): ParsedEnvelope {
        require(data.isNotEmpty()) { "SwapKit Cardano payload is empty" }
        if (data[0] != 0x84.toByte()) {
            throw SwapKitCardanoSignerException(
                "expected top-level array(4) (0x84), got 0x${(data[0].toInt() and 0xFF).toString(16)}"
            )
        }

        var offset = 1
        val bodyLen = cborItemLength(data, offset)
        val body = data.copyOfRange(offset, offset + bodyLen)
        offset += bodyLen

        offset += cborItemLength(data, offset) // witness_set (discarded; we build our own)

        val ivLen = cborItemLength(data, offset)
        val isValid = data.copyOfRange(offset, offset + ivLen)
        offset += ivLen

        val adLen = cborItemLength(data, offset)
        val auxData = data.copyOfRange(offset, offset + adLen)
        offset += adLen

        // Cardano envelopes are well-formed by construction; trailing bytes signal a malformed
        // payload, not forward compatibility.
        if (offset != data.size) {
            throw SwapKitCardanoSignerException(
                "trailing bytes after array(4): consumed $offset, total ${data.size}"
            )
        }

        return ParsedEnvelope(body = body, isValid = isValid, auxData = auxData)
    }

    /**
     * Byte length of the definite-length CBOR data item at [offset]. Cardano transactions never use
     * indefinite-length items, so an indefinite/reserved header is a malformed envelope.
     */
    private fun cborItemLength(data: ByteArray, offset: Int): Int {
        if (offset >= data.size)
            throw SwapKitCardanoSignerException("SwapKit Cardano CBOR is truncated")
        val start = offset
        var cursor = offset
        val head = data[cursor].toInt() and 0xFF
        cursor += 1
        val majorType = head shr 5
        val additionalInfo = head and 0x1F

        val argument: Long =
            when (additionalInfo) {
                in 0..23 -> additionalInfo.toLong()
                24 -> {
                    if (cursor >= data.size) throw SwapKitCardanoSignerException("CBOR truncated")
                    (data[cursor].toLong() and 0xFF).also { cursor += 1 }
                }
                25 -> readBE(data, cursor, 2).also { cursor += 2 }
                26 -> readBE(data, cursor, 4).also { cursor += 4 }
                27 -> readBE(data, cursor, 8).also { cursor += 8 }
                else ->
                    throw SwapKitCardanoSignerException(
                        "indefinite-length or reserved CBOR additional-info: $additionalInfo"
                    )
            }

        return when (majorType) {
            // unsigned int / negative int / simple-or-float — header only.
            0,
            1,
            7 -> cursor - start
            // byte string / text string — header + `argument` bytes payload.
            2,
            3 -> {
                val payload = argument.toInt()
                if (cursor + payload > data.size)
                    throw SwapKitCardanoSignerException("CBOR truncated")
                (cursor - start) + payload
            }
            // array: `argument` items follow.
            4 -> {
                var sub = cursor
                repeat(argument.toInt()) { sub += cborItemLength(data, sub) }
                sub - start
            }
            // map: `argument` (key, value) pairs follow.
            5 -> {
                var sub = cursor
                repeat(argument.toInt()) {
                    sub += cborItemLength(data, sub)
                    sub += cborItemLength(data, sub)
                }
                sub - start
            }
            // tag: header + one tagged item.
            6 -> (cursor - start) + cborItemLength(data, cursor)
            else -> throw SwapKitCardanoSignerException("unknown CBOR major type: $majorType")
        }
    }

    private fun readBE(data: ByteArray, offset: Int, bytes: Int): Long {
        if (offset + bytes > data.size) throw SwapKitCardanoSignerException("CBOR truncated")
        var value = 0L
        for (i in 0 until bytes) {
            value = (value shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        return value
    }

    private fun blake2b256(data: ByteArray): ByteArray {
        val blake = Blake2bDigest(256)
        blake.update(data, 0, data.size)
        return ByteArray(blake.digestSize).also { blake.doFinal(it, 0) }
    }

    /**
     * CBOR byte-string length prefix for the 32-byte vkey / 64-byte sig, matching iOS'
     * `CardanoSignedTxBuilder.cborBytes` (and the native send path).
     */
    private fun cborBytes(data: ByteArray): ByteArray {
        val length = data.size
        val header =
            when {
                length < 24 -> byteArrayOf((0x40 or length).toByte())
                length < 256 -> byteArrayOf(0x58.toByte(), length.toByte())
                else ->
                    byteArrayOf(
                        0x59.toByte(),
                        ((length shr 8) and 0xFF).toByte(),
                        (length and 0xFF).toByte(),
                    )
            }
        return header + data
    }

    private companion object {
        private const val PUBLIC_KEY_LENGTH = 32
        private const val SIGNATURE_LENGTH = 64
    }
}
