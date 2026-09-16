package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.common.hexToByteArrayOrNull
import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A dApp's Polkadot.js `SignerPayloadJSON`, carried verbatim in [KeysignPayload.memo] by the
 * extension's Substrate `signPayload` route. There is no proto slot for it: the extension
 * recognises the route from this shape alone (`useKeysignMutation.ts`), signs the extrinsic-payload
 * bytes it describes instead of building a transfer, and hands the raw signature back to the dApp
 * without broadcasting. A co-signer has to read the memo the same way or it hashes a transfer the
 * initiator never asked for and the ceremony signs two different messages.
 *
 * Fields are kept as the strings the dApp sent; the typed readers below are strict where the
 * extension's `hexToU8a` / `parseInt` are lenient, because a payload no real dApp produces is a
 * payload this device should refuse to sign rather than guess at.
 */
data class SubstrateSignerPayload(
    val method: String,
    val era: String,
    val nonce: String,
    val tip: String,
    val specVersion: String,
    val transactionVersion: String,
    val genesisHash: String,
    val blockHash: String,
    val blockNumber: String,
    val address: String,
    /** The memo exactly as received, for the raw view on Verify. */
    val rawJson: String,
) {
    fun methodBytes(): ByteArray = hexBytes("method", method)

    fun eraBytes(): ByteArray = hexBytes("era", era)

    fun genesisHashBytes(): ByteArray = hexBytes("genesisHash", genesisHash)

    fun blockHashBytes(): ByteArray = hexBytes("blockHash", blockHash)

    fun nonceValue(): Long = u32("nonce", nonce)

    fun specVersionValue(): Long = u32("specVersion", specVersion)

    fun transactionVersionValue(): Long = u32("transactionVersion", transactionVersion)

    /**
     * The tip as the extension's `BigInt(tip)` reads it: `0x`-prefixed hex or a decimal string, and
     * an absent or empty tip is zero.
     */
    fun tipValue(): BigInteger {
        if (tip.isEmpty()) return BigInteger.ZERO
        val value =
            if (tip.startsWith(HEX_PREFIX)) tip.removePrefix(HEX_PREFIX).toBigIntegerOrNull(16)
            else tip.toBigIntegerOrNull()
        return value?.takeIf { it.signum() >= 0 }
            ?: error("Substrate signer payload has a malformed tip")
    }

    /**
     * The pallet and call index the call bytes start with, `0x`-prefixed, or null when too short.
     */
    fun callIndexHex(): String? {
        val bytes = methodBytes()
        if (bytes.size < 2) return null
        return HEX_PREFIX + "%02x%02x".format(bytes[0], bytes[1])
    }

    private fun hexBytes(field: String, value: String): ByteArray =
        value.removePrefix(HEX_PREFIX).hexToByteArrayOrNull()
            ?: error("Substrate signer payload field $field is not hex")

    private fun u32(field: String, value: String): Long =
        value.removePrefix(HEX_PREFIX).toLongOrNull(16)?.takeIf { it in 0..U32_MAX }
            ?: error("Substrate signer payload field $field is not a u32")

    companion object {
        private const val HEX_PREFIX = "0x"
        private const val U32_MAX = 0xFFFF_FFFFL

        /**
         * Reads a signer payload out of a keysign memo under the extension's rule: the memo parses
         * as a JSON object whose `method` and `genesisHash` are non-empty. Anything else — a plain
         * text memo, a JSON scalar, a call with no genesis — is not this route and returns null so
         * the caller falls through to the native transfer path exactly as the extension does.
         */
        fun fromMemo(memo: String?): SubstrateSignerPayload? {
            if (memo.isNullOrEmpty()) return null
            val obj =
                try {
                    Json.parseToJsonElement(memo) as? JsonObject
                } catch (_: SerializationException) {
                    null
                } ?: return null
            val method = obj.string("method")
            val genesisHash = obj.string("genesisHash")
            if (method.isEmpty() || genesisHash.isEmpty()) return null
            return SubstrateSignerPayload(
                method = method,
                era = obj.string("era"),
                nonce = obj.string("nonce"),
                tip = obj.string("tip"),
                specVersion = obj.string("specVersion"),
                transactionVersion = obj.string("transactionVersion"),
                genesisHash = genesisHash,
                blockHash = obj.string("blockHash"),
                blockNumber = obj.string("blockNumber"),
                address = obj.string("address"),
                rawJson = memo,
            )
        }

        private fun JsonObject.string(key: String): String =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: ""
    }
}

/**
 * The dApp signer payload this keysign carries, or null for a native Substrate send. Only Polkadot
 * and Bittensor have the route (the two genesis hashes the extension accepts); a JSON-shaped memo
 * on any other chain is just a memo.
 */
val KeysignPayload.substrateDappPayload: SubstrateSignerPayload?
    get() =
        if (coin.chain == Chain.Polkadot || coin.chain == Chain.Bittensor) {
            SubstrateSignerPayload.fromMemo(memo)
        } else {
            null
        }
