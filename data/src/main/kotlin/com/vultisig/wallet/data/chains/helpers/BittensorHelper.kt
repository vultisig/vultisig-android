package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

/**
 * Bittensor signing helper — custom extrinsic builder.
 *
 * Bypasses TW Core's TransactionCompiler because Bittensor requires the CheckMetadataHash signed
 * extension which TW Core doesn't support.
 */
class BittensorHelper(private val vaultHexPublicKey: String) {

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val payload = buildSigningPayload(keysignPayload)
        // Substrate: if payload > 256 bytes, blake2b-256 hash it
        val toSign =
            if (payload.size > 256) {
                Utils.blake2bHash(payload)
            } else {
                payload
            }
        return listOf(Numeric.toHexStringNoPrefix(toSign))
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)

        val payload = buildSigningPayload(keysignPayload)
        val toSign =
            if (payload.size > 256) {
                Utils.blake2bHash(payload)
            } else {
                payload
            }
        val hashHex = Numeric.toHexStringNoPrefix(toSign)

        val signature =
            signatures[hashHex]?.getSignature() ?: throw Exception("Signature not found")

        if (!publicKey.verify(signature, toSign)) {
            throw Exception("Signature verification failed")
        }

        val polkadotSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Polkadot
                ?: throw IllegalArgumentException("Invalid blockChainSpecific")

        val callData = buildCallData(keysignPayload)
        val signedExtra = buildSignedExtra(polkadotSpecific)
        val signerPubkey = publicKey.data()

        val extrinsic = assembleExtrinsic(signerPubkey, signature, callData, signedExtra)
        val rawTx = Numeric.toHexStringNoPrefix(extrinsic)
        val txHash = Numeric.toHexString(Utils.blake2bHash(extrinsic))

        return SignedTransactionResult(rawTransaction = rawTx, transactionHash = txHash)
    }

    fun getZeroSignedTransaction(keysignPayload: KeysignPayload): String {
        val polkadotSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Polkadot
                ?: throw IllegalArgumentException("Invalid blockChainSpecific")

        val callData = buildCallData(keysignPayload)
        val signedExtra = buildSignedExtra(polkadotSpecific)
        val dummySigner = ByteArray(32)
        val dummySignature = ByteArray(64)

        val extrinsic = assembleExtrinsic(dummySigner, dummySignature, callData, signedExtra)
        return Numeric.toHexStringNoPrefix(extrinsic)
    }

    private fun buildSigningPayload(keysignPayload: KeysignPayload): ByteArray {
        val polkadotSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Polkadot
                ?: throw IllegalArgumentException("Invalid blockChainSpecific")

        val callData = buildCallData(keysignPayload)
        val signedExtra = buildSignedExtra(polkadotSpecific)
        val additionalSigned = buildAdditionalSigned(polkadotSpecific)

        return callData + signedExtra + additionalSigned
    }

    /**
     * Call data for Balances.transfer_allow_death(dest, value) Encoding: [pallet:5, call:0] ++
     * MultiAddress::Id(0x00) ++ dest(32B) ++ compact(amount)
     */
    private fun buildCallData(keysignPayload: KeysignPayload): ByteArray {
        val destBytes = ss58Decode(keysignPayload.toAddress)
        val amount = keysignPayload.toAmount

        val out = ByteArrayOutputStream()
        out.write(BALANCES_PALLET.toInt())
        out.write(TRANSFER_ALLOW_DEATH.toInt())
        out.write(MULTI_ADDRESS_ID.toInt()) // MultiAddress::Id
        out.write(destBytes)
        out.write(compactEncode(amount))
        return out.toByteArray()
    }

    /**
     * Signed extensions (extra) in the extrinsic body. Era | Nonce(compact) | Tip(compact, 0) |
     * CheckMetadataHash(0x00)
     */
    private fun buildSignedExtra(specific: BlockChainSpecific.Polkadot): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encodeMortalEra(specific.currentBlockNumber.toLong(), 64))
        out.write(compactEncode(specific.nonce))
        out.write(compactEncode(BigInteger.ZERO)) // tip = 0
        out.write(METADATA_HASH_DISABLED.toInt()) // CheckMetadataHash: Disabled
        return out.toByteArray()
    }

    /**
     * Additional signed data for the signing payload. specVersion(u32le) | txVersion(u32le) |
     * genesisHash(32B) | blockHash(32B) | CheckMetadataHash(0x00)
     */
    private fun buildAdditionalSigned(specific: BlockChainSpecific.Polkadot): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(uint32LE(specific.specVersion.toInt()))
        out.write(uint32LE(specific.transactionVersion.toInt()))
        out.write(hexToBytes(specific.genesisHash))
        out.write(hexToBytes(specific.recentBlockHash))
        out.write(METADATA_HASH_DISABLED.toInt()) // CheckMetadataHash additional signed
        return out.toByteArray()
    }

    /**
     * Assemble final signed extrinsic. compactLen | 0x84 | MultiAddress::Id(signer) |
     * MultiSignature::Ed25519(sig) | signedExtra | callData
     */
    private fun assembleExtrinsic(
        signerPubkey: ByteArray,
        signature: ByteArray,
        callData: ByteArray,
        signedExtra: ByteArray,
    ): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(0x84) // signed extrinsic, version 4
        body.write(MULTI_ADDRESS_ID.toInt())
        body.write(signerPubkey)
        body.write(MULTI_SIGNATURE_ED25519.toInt())
        body.write(signature)
        body.write(signedExtra)
        body.write(callData)

        val bodyBytes = body.toByteArray()
        val lengthPrefix = compactEncode(bodyBytes.size.toBigInteger())
        return lengthPrefix + bodyBytes
    }

    companion object {
        private const val BALANCES_PALLET: Byte = 5
        private const val TRANSFER_ALLOW_DEATH: Byte = 0
        private const val MULTI_ADDRESS_ID: Byte = 0x00
        private const val MULTI_SIGNATURE_ED25519: Byte = 0x00
        private const val METADATA_HASH_DISABLED: Byte = 0x00
        const val DEFAULT_FEE_RAO = 200_000L
        private const val SS58_PREFIX = 42

        private fun compactEncode(value: BigInteger): ByteArray {
            return when {
                value < BigInteger.valueOf(64) -> byteArrayOf((value.toInt() shl 2).toByte())
                value < BigInteger.valueOf(16384) -> {
                    val v = (value.toLong() shl 2) or 1L
                    byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
                }
                value < BigInteger.valueOf(1073741824) -> {
                    val v = (value.toLong() shl 2) or 2L
                    byteArrayOf(
                        (v and 0xFF).toByte(),
                        ((v shr 8) and 0xFF).toByte(),
                        ((v shr 16) and 0xFF).toByte(),
                        ((v shr 24) and 0xFF).toByte(),
                    )
                }
                else -> {
                    val bytes =
                        value.toByteArray().let { b ->
                            // BigInteger is big-endian, reverse to little-endian
                            // Remove leading zero byte if present
                            val trimmed =
                                if (b[0] == 0.toByte() && b.size > 1) b.drop(1).toByteArray() else b
                            trimmed.reversedArray()
                        }
                    val prefix = ((bytes.size - 4) shl 2) or 3
                    byteArrayOf(prefix.toByte()) + bytes
                }
            }
        }

        private fun encodeMortalEra(blockNumber: Long, period: Int): ByteArray {
            // Round UP to next power of 2 (matching Substrate spec)
            val calPeriod =
                (if (period > 0 && (period and (period - 1)) == 0) period
                    else Integer.highestOneBit(period) shl 1)
                    .coerceIn(4, 65536)
            val phase = (blockNumber % calPeriod).toInt()
            val quantizeFactor = (calPeriod shr 12).coerceAtLeast(1)
            val quantizedPhase = (phase / quantizeFactor) * quantizeFactor
            val encoded =
                (Integer.numberOfTrailingZeros(calPeriod) - 1).coerceIn(1, 15) +
                    ((quantizedPhase / quantizeFactor) shl 4)
            return byteArrayOf((encoded and 0xFF).toByte(), ((encoded shr 8) and 0xFF).toByte())
        }

        private fun uint32LE(value: Int): ByteArray {
            return byteArrayOf(
                (value and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 24) and 0xFF).toByte(),
            )
        }

        /**
         * SS58 encode raw 32-byte pubkey with Bittensor prefix (42). Format: base58(prefix_byte ++
         * pubkey ++ blake2b_checksum[0..2])
         */
        fun ss58Encode(pubkey: ByteArray): String {
            require(pubkey.size == 32) { "SS58 encode requires 32-byte pubkey, got ${pubkey.size}" }
            val prefixByte = byteArrayOf(SS58_PREFIX.toByte())
            val payload = prefixByte + pubkey
            // Checksum = blake2b-512("SS58PRE" + payload)[0..2]
            val checksumInput = "SS58PRE".toByteArray() + payload
            val hash = wallet.core.jni.Hash.blake2b(checksumInput, 64)
            val checksum = hash.sliceArray(0..1)
            return base58Encode(payload + checksum)
        }

        private fun base58Encode(data: ByteArray): String {
            var n = BigInteger(1, data) // positive big-endian
            val sb = StringBuilder()
            while (n > BigInteger.ZERO) {
                val (quot, rem) = n.divideAndRemainder(BigInteger.valueOf(58))
                sb.append(BASE58_ALPHABET[rem.toInt()])
                n = quot
            }
            // Leading zeros
            for (b in data) {
                if (b == 0.toByte()) sb.append('1') else break
            }
            return sb.reverse().toString()
        }

        fun hexToBytes(hex: String): ByteArray {
            val clean = if (hex.startsWith("0x")) hex.substring(2) else hex
            return ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }

        /**
         * Decode SS58 address to raw 32-byte public key. SS58 = base58(prefix ++ pubkey ++
         * checksum)
         */
        private fun ss58Decode(address: String): ByteArray {
            val decoded = base58Decode(address)
            // For prefix < 64: 1 byte prefix + 32 bytes pubkey + 2 bytes checksum = 35 bytes
            // For prefix 64-16383: 2 byte prefix + 32 bytes pubkey + 2 bytes checksum = 36 bytes
            val prefixLen: Int
            val pubkey: ByteArray
            val checksum: ByteArray
            when {
                decoded.size == 35 -> {
                    prefixLen = 1
                    pubkey = decoded.sliceArray(1..32)
                    checksum = decoded.sliceArray(33..34)
                }
                decoded.size == 36 -> {
                    prefixLen = 2
                    pubkey = decoded.sliceArray(2..33)
                    checksum = decoded.sliceArray(34..35)
                }
                else ->
                    throw IllegalArgumentException("Invalid SS58 address length: ${decoded.size}")
            }
            // Verify blake2b-512 checksum
            val payload = decoded.sliceArray(0 until prefixLen + 32)
            val hash = wallet.core.jni.Hash.blake2b("SS58PRE".toByteArray() + payload, 64)
            if (!hash.sliceArray(0..1).contentEquals(checksum)) {
                throw IllegalArgumentException("Invalid SS58 checksum for address: $address")
            }
            return pubkey
        }

        private val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

        private fun base58Decode(input: String): ByteArray {
            var result = BigInteger.ZERO
            for (c in input) {
                val index = BASE58_ALPHABET.indexOf(c)
                if (index < 0) throw IllegalArgumentException("Invalid base58 character: $c")
                result =
                    result.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(index.toLong()))
            }
            val bytes = result.toByteArray()
            // Remove leading zero from BigInteger sign byte
            val trimmed =
                if (bytes[0] == 0.toByte() && bytes.size > 1) bytes.drop(1).toByteArray() else bytes
            // Count leading '1's in input (each = leading zero byte)
            val leadingZeros = input.takeWhile { it == '1' }.length
            return ByteArray(leadingZeros) + trimmed
        }
    }
}
