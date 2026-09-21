package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.crypto.Base58Codec
import org.bouncycastle.crypto.digests.Blake2bDigest

/**
 * SS58 over a raw 32-byte AccountId, spelled the way `@polkadot/util-crypto`'s `encodeAddress`
 * spells it: `base58(prefix ‖ id ‖ blake2b-512("SS58PRE" ‖ prefix ‖ id)[0..2])`.
 *
 * Deliberately not WalletCore's `AnyAddress`. That route only accepts an ed25519 `PublicKey` and
 * yields `""` for an AccountId that is not an Edwards point — an sr25519 key (polkadot.js's
 * default), a multisig, a proxy or a pallet account, about half of the recipients a dApp transfer
 * can name. An SS58 address is a checksum over bytes, not a curve point, so nothing here refuses
 * one. Pure Kotlin so the rules built on it stay unit-testable off-device.
 */
object Ss58 {

    private const val ACCOUNT_ID_LENGTH = 32
    private const val CHECKSUM_LENGTH = 2
    private const val DIGEST_LENGTH_BITS = 512

    /** Prefixes up to this value are a single byte; larger ones use the two-byte form. */
    private const val SINGLE_BYTE_PREFIX_MAX = 63

    private val CHECKSUM_PREIMAGE_PREFIX = "SS58PRE".toByteArray()

    fun encode(accountId: ByteArray, prefix: Int): String {
        require(accountId.size == ACCOUNT_ID_LENGTH) {
            "SS58 encodes a 32-byte AccountId, got ${accountId.size} bytes"
        }
        require(prefix in 0..SINGLE_BYTE_PREFIX_MAX) {
            "SS58 prefix $prefix needs the two-byte form, which nothing here uses"
        }
        val payload = byteArrayOf(prefix.toByte()) + accountId
        val digest = Blake2bDigest(DIGEST_LENGTH_BITS)
        digest.update(CHECKSUM_PREIMAGE_PREFIX, 0, CHECKSUM_PREIMAGE_PREFIX.size)
        digest.update(payload, 0, payload.size)
        val checksum = ByteArray(digest.digestSize).also { digest.doFinal(it, 0) }
        return Base58Codec.encode(payload + checksum.copyOfRange(0, CHECKSUM_LENGTH))
    }
}
