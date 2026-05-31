@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import tss.KeysignResponse
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

internal class SwapKitTronSignerException(message: String) : Exception(message)

/**
 * Bridges SwapKit's pre-built TRON transaction onto Android's signing path. Ported from iOS'
 * `SwapKitTronSigner`. SwapKit's `/v3/swap` returns a TronWeb-shaped object (`{txID, raw_data
 * {...}, raw_data_hex, visible?}`) which the quote source JSON-encodes verbatim into
 * [SwapKitSwapPayloadJson.txPayload].
 *
 * The canonical Tron signing input is `sha256(raw_data_bytes)` — which is also the `txID` — so we
 * hash SwapKit's `raw_data_hex` directly (option B in iOS' consolidated-signing plan) rather than
 * round-tripping `raw_data` back through a `Tron.SigningInput` proto. Trusting the pre-built
 * `raw_data_hex` keeps the surface area small (no need to reimplement every `contract.type` SwapKit
 * might emit — `TriggerSmartContract`, `TransferContract`, …) and gives the cosigning peer
 * identical bytes to verify against. The signed broadcast envelope is the original object with the
 * 65-byte `r||s||v` signature appended as `signature: [hex]`, which is exactly what
 * [com.vultisig.wallet.data.api.TronApi.broadcastTransaction] posts to
 * `/wallet/broadcasttransaction`.
 *
 * Trade-off: SwapKit bakes its own `fee_limit` (typically 10 TRX for TRC-20 routes) into
 * `raw_data_hex` and we don't second-guess it; a tight limit is logged so an OUT_OF_ENERGY revert
 * is traceable in support logs, but it doesn't fail the sign.
 */
internal class SwapKitTronSigner(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    private val coinType = CoinType.TRON

    /**
     * Tron signing digest = `sha256(raw_data_bytes)`, hex-encoded to match the keysign message-hash
     * convention. Single hash per transaction.
     */
    fun getPreSignedImageHash(txPayload: ByteArray): List<String> =
        listOf(Numeric.toHexStringNoPrefix(digest(txPayload)))

    /**
     * Assemble the broadcast-format TronWeb envelope from SwapKit's pre-built object plus the MPC
     * ECDSA signature. Verifies the signature against the derived vault key before re-encoding so a
     * wrong-key cosignature can't be broadcast.
     */
    fun getSignedTransaction(
        txPayload: ByteArray,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val digest = digest(txPayload)
        val key = Numeric.toHexStringNoPrefix(digest)
        val signature =
            signatures[key]?.getSignatureWithRecoveryID()
                ?: throw SwapKitTronSignerException(
                    "Missing signature for Tron digest ${key.take(16)}…"
                )
        // Tron broadcast expects a 65-byte r||s||v signature; getSignatureWithRecoveryID yields
        // exactly that.
        if (signature.size != 65) {
            throw SwapKitTronSignerException(
                "Tron signature must be 65 bytes (r||s||v); got ${signature.size}"
            )
        }
        val publicKey = derivedPublicKey()
        if (!publicKey.verify(signature, digest)) {
            throw SwapKitTronSignerException("SwapKit TRON signature verification failed")
        }

        logTightFeeLimit(txPayload)

        return SignedTransactionResult(
            rawTransaction =
                makeBroadcastEnvelope(txPayload, Numeric.toHexStringNoPrefix(signature)),
            transactionHash = key,
        )
    }

    /**
     * Tron signing digest = `sha256(raw_data_bytes)`. Exposed (internal) so a unit test can pin the
     * digest to SwapKit's reported `txID` (Tron's txID is exactly this digest) without the
     * WalletCore JNI.
     */
    internal fun digest(txPayload: ByteArray): ByteArray {
        val rawDataHex = parseRawDataHex(txPayload)
        val rawDataBytes =
            runCatching { Numeric.hexStringToByteArray(rawDataHex) }
                .getOrElse {
                    throw SwapKitTronSignerException("SwapKit TRON raw_data_hex is not valid hex")
                }
        if (rawDataBytes.isEmpty()) {
            throw SwapKitTronSignerException("SwapKit TRON raw_data_hex is empty")
        }
        return sha256(rawDataBytes)
    }

    /**
     * Re-encode the original TronWeb object with `signature: [hex]` appended. Preserves the
     * existing fields verbatim so the cosigning peer broadcasts the same canonical object — only
     * the signature is added.
     */
    internal fun makeBroadcastEnvelope(txPayload: ByteArray, signatureHex: String): String {
        val obj = parsePayloadObject(txPayload)
        val merged =
            JsonObject(obj + ("signature" to JsonArray(listOf(JsonPrimitive(signatureHex)))))
        return json.encodeToString(JsonObject.serializer(), merged)
    }

    private fun parsePayloadObject(txPayload: ByteArray): JsonObject {
        if (txPayload.isEmpty()) {
            throw SwapKitTronSignerException("SwapKit TRON payload is empty")
        }
        val element =
            runCatching { json.parseToJsonElement(txPayload.decodeToString()) }
                .getOrElse {
                    throw SwapKitTronSignerException("SwapKit TRON payload is not valid JSON")
                }
        return element as? JsonObject
            ?: throw SwapKitTronSignerException("SwapKit TRON payload is not a JSON object")
    }

    private fun parseRawDataHex(txPayload: ByteArray): String =
        parsePayloadObject(txPayload)["raw_data_hex"]
            ?.jsonPrimitive
            ?.takeIf { it.isString }
            ?.content
            ?: throw SwapKitTronSignerException("SwapKit TRON payload is missing raw_data_hex")

    /**
     * Surface a suspiciously low `raw_data.fee_limit` (< 20 TRX) so post-mortem logs catch an
     * OUT_OF_ENERGY revert. Best-effort only: a parse miss is silently ignored — the fee_limit is
     * SwapKit's to pick and never blocks the sign. Mirrors iOS' warning.
     */
    private fun logTightFeeLimit(txPayload: ByteArray) {
        val feeLimit =
            runCatching {
                    parsePayloadObject(txPayload)["raw_data"]
                        ?.let { it as? JsonObject }
                        ?.get("fee_limit")
                        ?.jsonPrimitive
                        ?.content
                        ?.toLongOrNull()
                }
                .getOrNull() ?: return
        if (feeLimit in 1 until TIGHT_FEE_LIMIT_SUN) {
            Timber.w("SwapKit TRON fee_limit looks tight: %d sun", feeLimit)
        }
    }

    private fun derivedPublicKey(): PublicKey {
        val derivedHex =
            PublicKeyHelper.getDerivedPublicKey(
                vaultHexPublicKey,
                vaultHexChainCode,
                coinType.derivationPath(),
            )
        return PublicKey(Numeric.hexStringToByteArray(derivedHex), PublicKeyType.SECP256K1)
            .uncompressed()
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private companion object {
        /**
         * 20 TRX in sun — SwapKit TRC-20 routes typically set 10 TRX, so below this is worth a log.
         */
        private const val TIGHT_FEE_LIMIT_SUN = 20_000_000L
        private val json = Json { ignoreUnknownKeys = true }
    }
}
