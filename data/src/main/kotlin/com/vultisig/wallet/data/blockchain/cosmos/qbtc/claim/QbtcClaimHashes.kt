package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import com.vultisig.wallet.data.crypto.Ripemd160
import java.security.MessageDigest

/**
 * Domain-separated hash construction for the QBTC claim flow. Mirrors
 * `vultisig-sdk/.../claim/computeClaimHashes.ts` byte-for-byte; the tags MUST match
 * `x/qbtc/zk/message.go` on the chain side.
 *
 * ```
 * messageHash = SHA256(
 *     "ecdsa-hash160:" ‖ addressHash(20) ‖ qbtcAddressHash(32) ‖ chainIdHash(8) ‖ "qbtc-claim-v1"
 * )
 * ```
 */
internal data class QbtcClaimHashes(
    val messageHash: ByteArray,
    val addressHash: ByteArray,
    val qbtcAddressHash: ByteArray,
    val circuit: QbtcClaimCircuit,
) {
    companion object {
        private fun sha256(data: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(data)

        /** Hash160 = RIPEMD160(SHA256(data)) — the standard Bitcoin hash. */
        private fun hash160(data: ByteArray): ByteArray = Ripemd160.hash(sha256(data))

        /**
         * ECDSA circuit → Hash160(compressedPubkey) (20 bytes). Schnorr circuit → x-only pubkey,
         * the last 32 bytes of the compressed key.
         */
        fun computeAddressHash(compressedPubkey: ByteArray, circuit: QbtcClaimCircuit): ByteArray {
            require(
                compressedPubkey.size == 33 &&
                    (compressedPubkey[0] == 0x02.toByte() || compressedPubkey[0] == 0x03.toByte())
            ) {
                "compressedPubkey must be a 33-byte compressed secp256k1 key (first byte 0x02 or 0x03)"
            }
            return when (circuit) {
                QbtcClaimCircuit.SCHNORR -> compressedPubkey.copyOfRange(1, 33)
                QbtcClaimCircuit.ECDSA -> hash160(compressedPubkey)
            }
        }

        /** SHA-256 of the QBTC bech32 address string (UTF-8 bytes). 32 bytes. */
        fun computeQbtcAddressHash(qbtcAddress: String): ByteArray =
            sha256(qbtcAddress.toByteArray(Charsets.UTF_8))

        /** First 8 bytes of SHA-256 of the chain id — truncation, not the full digest. */
        fun computeChainIdHash(chainId: String): ByteArray =
            sha256(chainId.toByteArray(Charsets.UTF_8))
                .copyOf(QbtcClaimConfig.CHAIN_ID_HASH_PREFIX_BYTES)

        /**
         * Final claim message hash. Throws on the Schnorr circuit — the chain has not defined a
         * Schnorr tag yet (btcq-org/qbtc#148).
         */
        fun computeClaimMessageHash(
            addressHash: ByteArray,
            qbtcAddressHash: ByteArray,
            chainIdHash: ByteArray,
            circuit: QbtcClaimCircuit,
        ): ByteArray {
            if (circuit == QbtcClaimCircuit.SCHNORR) {
                throw SchnorrClaimUnsupportedException()
            }
            require(addressHash.size == 20) {
                "addressHash must be 20 bytes, got ${addressHash.size}"
            }
            require(qbtcAddressHash.size == 32) {
                "qbtcAddressHash must be 32 bytes, got ${qbtcAddressHash.size}"
            }
            require(chainIdHash.size == QbtcClaimConfig.CHAIN_ID_HASH_PREFIX_BYTES) {
                "chainIdHash must be ${QbtcClaimConfig.CHAIN_ID_HASH_PREFIX_BYTES} bytes, got ${chainIdHash.size}"
            }

            val prefix = QbtcClaimConfig.DOMAIN_TAG_PREFIX.toByteArray(Charsets.UTF_8)
            val suffix = QbtcClaimConfig.DOMAIN_TAG_SUFFIX.toByteArray(Charsets.UTF_8)
            val message = prefix + addressHash + qbtcAddressHash + chainIdHash + suffix
            return sha256(message)
        }

        /** Computes every hash needed for a claim from the raw inputs. */
        fun computeAll(
            btcAddress: String,
            compressedPubkey: ByteArray,
            qbtcAddress: String,
            chainId: String,
        ): QbtcClaimHashes {
            val circuit = BtcAddressType.detect(btcAddress).circuit
            val addressHash = computeAddressHash(compressedPubkey, circuit)
            val qbtcAddressHash = computeQbtcAddressHash(qbtcAddress)
            val chainIdHash = computeChainIdHash(chainId)
            val messageHash =
                computeClaimMessageHash(addressHash, qbtcAddressHash, chainIdHash, circuit)
            return QbtcClaimHashes(messageHash, addressHash, qbtcAddressHash, circuit)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QbtcClaimHashes) return false
        return messageHash.contentEquals(other.messageHash) &&
            addressHash.contentEquals(other.addressHash) &&
            qbtcAddressHash.contentEquals(other.qbtcAddressHash) &&
            circuit == other.circuit
    }

    override fun hashCode(): Int {
        var result = messageHash.contentHashCode()
        result = 31 * result + addressHash.contentHashCode()
        result = 31 * result + qbtcAddressHash.contentHashCode()
        result = 31 * result + circuit.hashCode()
        return result
    }
}

internal class SchnorrClaimUnsupportedException :
    IllegalArgumentException(
        "Schnorr / Taproot claim circuit is not yet supported on the QBTC chain"
    )
