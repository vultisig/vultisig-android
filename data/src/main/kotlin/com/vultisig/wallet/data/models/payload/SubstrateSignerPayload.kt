package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.common.hexToByteArrayOrNull
import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
    /**
     * The runtime's signed extensions as polkadot.js lists them. Only `CheckMetadataHash` changes
     * the signed bytes; every other relay / Bittensor extension is either byte-less or already
     * covered by the fixed fields above.
     */
    val signedExtensions: List<String>,
    /**
     * `CheckMetadataHash` mode (`u8`, 0 = disabled), present only when that extension is listed.
     */
    val mode: String,
    /** `CheckMetadataHash` hash, or empty for polkadot.js's `null`. */
    val metadataHash: String,
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
            if (tip.startsWith(HEX_PREFIX, ignoreCase = true)) {
                tip.removeHexPrefix().toBigIntegerOrNull(16)
            } else {
                tip.toBigIntegerOrNull()
            }
        return value?.takeIf { it.signum() >= 0 }
            ?: error("Substrate signer payload has a malformed tip")
    }

    /**
     * True when the runtime signs `CheckMetadataHash` (Polkadot relay since spec 1_002_000,
     * Bittensor likewise). polkadot.js then puts `mode` after the tip and `Option<metadataHash>`
     * after the block hash — see [modeByte] and [metadataHashOption].
     */
    val hasCheckMetadataHash: Boolean
        get() = CHECK_METADATA_HASH in signedExtensions

    /** The `CheckMetadataHash` `mode` as a `u8`; an absent mode is 0 (disabled). */
    fun modeByte(): Byte {
        if (mode.isEmpty()) return 0
        val value =
            mode.toIntOrNull()?.takeIf { it in 0..U8_MAX }
                ?: error("Substrate signer payload has a malformed mode")
        return value.toByte()
    }

    /**
     * The `CheckMetadataHash` additional-signed bytes: SCALE `Option<[u8;32]>`, `0x00` for
     * polkadot.js's `null` and `0x01` followed by the hash otherwise.
     */
    fun metadataHashOption(): ByteArray {
        if (metadataHash.isEmpty()) return byteArrayOf(OPTION_NONE)
        val hash = hexBytes("metadataHash", metadataHash)
        check(hash.size == HASH_LENGTH) { "Substrate signer payload metadataHash is not 32 bytes" }
        return byteArrayOf(OPTION_SOME) + hash
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
        value.removeHexPrefix().hexToByteArrayOrNull()
            ?: error("Substrate signer payload field $field is not hex")

    private fun u32(field: String, value: String): Long =
        value.removeHexPrefix().toLongOrNull(16)?.takeIf { it in 0..U32_MAX }
            ?: error("Substrate signer payload field $field is not a u32")

    /**
     * Strips `0x` in either case, as JavaScript's `parseInt(…, 16)` / `BigInt(…)` do and as the
     * chain binding in [substrateDappPayload] does — one gate must not accept what the next
     * refuses.
     */
    private fun String.removeHexPrefix(): String =
        if (startsWith(HEX_PREFIX, ignoreCase = true)) drop(HEX_PREFIX.length) else this

    companion object {
        private const val HEX_PREFIX = "0x"
        private const val U32_MAX = 0xFFFF_FFFFL
        private const val U8_MAX = 0xFF
        private const val HASH_LENGTH = 32
        private const val OPTION_NONE: Byte = 0x00
        private const val OPTION_SOME: Byte = 0x01
        private const val CHECK_METADATA_HASH = "CheckMetadataHash"

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
                signedExtensions =
                    (obj["signedExtensions"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                        .orEmpty(),
                // polkadot.js sends `mode` as a JSON number, so it is read as a scalar of any kind.
                mode = (obj["mode"] as? JsonPrimitive)?.contentOrNull ?: "",
                metadataHash = obj.string("metadataHash"),
                rawJson = memo,
            )
        }

        /**
         * The dApp signer payload a keysign on [chain] with this [memo] carries, or null for a
         * native Substrate send. Only Polkadot and Bittensor have the route; a JSON-shaped memo on
         * any other chain is just a memo.
         *
         * The payload's `genesisHash` must be the one [chain] signs for. The chain is what Verify
         * names and what the co-signer consents to, while the genesis hash is what the signature is
         * actually bound to — and both chains sign with the same ed25519 key, so a payload labelled
         * Polkadot carrying Bittensor's genesis would produce a signature valid on Bittensor. The
         * extension derives the chain from the genesis hash at intake and refuses any other; a
         * mismatch here is a payload no honest initiator sends, and it is refused rather than
         * signed.
         */
        fun fromKeysign(chain: Chain, memo: String?): SubstrateSignerPayload? {
            val genesisHash = substrateGenesisHashByChain[chain] ?: return null
            val payload = fromMemo(memo) ?: return null
            check(payload.genesisHash.equals(genesisHash, ignoreCase = true)) {
                "Substrate signer payload genesis hash does not belong to ${chain.raw}"
            }
            return payload
        }

        private fun JsonObject.string(key: String): String =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: ""
    }
}

/**
 * The genesis hash each Substrate chain's dApp route signs for — the same two the extension's
 * `substrateChainByGenesisHash` accepts (Polkadot is the relay chain, not Asset Hub).
 */
private val substrateGenesisHashByChain =
    mapOf(
        Chain.Polkadot to "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
        Chain.Bittensor to "0x2f0555cc76fc2840a25a6ea3b9637146806f1f44b090c175ffde2a7e5ab36c03",
    )

/** The dApp signer payload this keysign carries — see [SubstrateSignerPayload.fromKeysign]. */
val KeysignPayload.substrateDappPayload: SubstrateSignerPayload?
    get() = SubstrateSignerPayload.fromKeysign(coin.chain, memo)
