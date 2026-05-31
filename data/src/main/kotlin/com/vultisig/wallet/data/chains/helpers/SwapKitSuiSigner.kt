@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.tss.getSignature
import java.util.Base64
import org.bouncycastle.crypto.digests.Blake2bDigest
import tss.KeysignResponse
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

internal class SwapKitSuiSignerException(message: String) : Exception(message)

/**
 * Signs SwapKit's pre-built Sui programmable transaction block (PTB). Ported from iOS'
 * `SwapKitSuiSigner`. WalletCore's `Sui.SigningInput` only models structured `Pay` / `PaySui` flows
 * — it can't take a serialized PTB and produce a sighash — so we compute Sui's signing digest
 * ourselves, feed it to the MPC engine, then assemble the submit-format signature envelope around
 * the resulting Ed25519 signature (the same hash-and-sign pattern the QBTC claim path uses).
 *
 * Sui signing intent (from the Sui spec):
 * ```
 *   intent_message = [scope=0 (TransactionData), version=0 (V0), app=0 (Sui)] || bcs(transaction_data)
 *   digest         = blake2b_32(intent_message)
 * ```
 *
 * SwapKit returns the BCS-serialized transaction data as a base64 string, base64-decoded into
 * [SwapKitSwapPayloadJson.txPayload] by `SwapKitQuoteSource`. We prepend the three intent-prefix
 * bytes and hash with Blake2b-32 (Bouncy Castle, identical to WalletCore's `Hash.blake2b(_, 32)` —
 * keeps the digest path JNI-free and unit-testable). The submit-format envelope is `[flag=0x00
 * ed25519, sig (64 bytes), pubkey (32 bytes)]`, base64-encoded — this is the `signatures[0]`
 * argument to `sui_executeTransactionBlock`; the `tx_bytes` argument is the same base64 PTB SwapKit
 * handed us, passed verbatim ([SignedTransactionResult.rawTransaction]).
 */
internal class SwapKitSuiSigner(private val vaultHexPublicKey: String) {

    /**
     * Sui signing digest = `blake2b_32(intent_prefix || ptb)`. A single-element list because every
     * Sui transaction signs exactly one digest; hex-encoded to match the keysign message-hash
     * convention.
     */
    fun getPreSignedImageHash(ptbBytes: ByteArray): List<String> =
        listOf(digest(ptbBytes).toHexString())

    /**
     * Assemble the Sui submit-format signed transaction. [rawTransaction][SignedTransactionResult]
     * is the base64 PTB (passed verbatim to the RPC); [signature][SignedTransactionResult] is the
     * base64 Ed25519 envelope. The transaction hash stays empty — Sui's canonical tx digest only
     * resolves after the RPC accepts the submission (same convention as [SuiHelper]).
     */
    fun getSignedTransaction(
        ptbBytes: ByteArray,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val pubKeyData = vaultHexPublicKey.hexToByteArray()
        val publicKey = PublicKey(pubKeyData, PublicKeyType.ED25519)

        val digest = digest(ptbBytes)
        val key = digest.toHexString()
        val signature =
            signatures[key]?.getSignature()
                ?: throw SwapKitSuiSignerException(
                    "Missing signature for Sui digest ${key.take(16)}…"
                )
        if (!publicKey.verify(signature, digest)) {
            throw SwapKitSuiSignerException("SwapKit Sui signature verification failed")
        }

        // Envelope: [flag=0x00 ed25519] + sig (64 bytes) + pubkey (32 bytes).
        val envelope = byteArrayOf(ED25519_SCHEME_FLAG) + signature + pubKeyData
        return SignedTransactionResult(
            rawTransaction = Base64.getEncoder().encodeToString(ptbBytes),
            transactionHash = "",
            signature = Base64.getEncoder().encodeToString(envelope),
        )
    }

    /**
     * Sui signing digest = Blake2b-32 of `intent_prefix || ptb_bytes`. The PTB bytes are hashed
     * verbatim — SwapKit hands us already-BCS-serialized transaction data, which is exactly what
     * the intent message wraps. Exposed (internal) so a unit test can pin the digest without JNI.
     */
    internal fun digest(ptbBytes: ByteArray): ByteArray {
        require(ptbBytes.isNotEmpty()) { "SwapKit Sui payload is empty" }
        val message = INTENT_PREFIX + ptbBytes
        val blake = Blake2bDigest(256)
        blake.update(message, 0, message.size)
        val out = ByteArray(blake.digestSize)
        blake.doFinal(out, 0)
        return out
    }

    private companion object {
        /**
         * Sui transaction-data intent prefix: scope=0 (TransactionData), version=0 (V0), app=0
         * (Sui).
         */
        private val INTENT_PREFIX = byteArrayOf(0x00, 0x00, 0x00)

        /** Ed25519 signature-scheme flag in Sui's signature envelope. */
        private const val ED25519_SCHEME_FLAG: Byte = 0x00
    }
}
