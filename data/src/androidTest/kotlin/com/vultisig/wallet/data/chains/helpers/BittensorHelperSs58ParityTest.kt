package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.WalletCoreNative
import java.math.BigInteger
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Parity coverage for #5667: [BittensorHelper.ss58Encode]/[BittensorHelper.ss58Decode] now delegate
 * to WalletCore's `AnyAddress` instead of the hand-rolled base58/blake2b implementation that used
 * to live here. This pins the old implementation (kept only in this test) as a reference oracle and
 * checks the new one against it, since the one risk the swap carries — whether `AnyAddress.data()`
 * returns the raw 32-byte account id rather than a hash of it — can only be settled by running both
 * side by side.
 */
class BittensorHelperSs58ParityTest {

    @Before
    fun loadWalletCore() {
        WalletCoreNative.ensureLoaded()
    }

    private object ReferenceImpl {
        private const val SS58_PREFIX = 42
        private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

        fun encode(pubkey: ByteArray): String {
            val payload = byteArrayOf(SS58_PREFIX.toByte()) + pubkey
            val hash = wallet.core.jni.Hash.blake2b("SS58PRE".toByteArray() + payload, 64)
            return base58Encode(payload + hash.sliceArray(0..1))
        }

        private fun base58Encode(data: ByteArray): String {
            var n = BigInteger(1, data)
            val sb = StringBuilder()
            while (n > BigInteger.ZERO) {
                val (quot, rem) = n.divideAndRemainder(BigInteger.valueOf(58))
                sb.append(ALPHABET[rem.toInt()])
                n = quot
            }
            for (b in data) {
                if (b == 0.toByte()) sb.append('1') else break
            }
            return sb.reverse().toString()
        }
    }

    private fun randomPubkey(seed: Long): ByteArray {
        val bytes = ByteArray(32)
        SecureRandom.getInstance("SHA1PRNG").apply { setSeed(seed) }.nextBytes(bytes)
        return bytes
    }

    @Test
    fun encode_matches_the_hand_rolled_reference_for_random_pubkeys() {
        for (seed in 1L..20L) {
            val pubkey = randomPubkey(seed)
            assertEquals(
                "seed $seed",
                ReferenceImpl.encode(pubkey),
                BittensorHelper.ss58Encode(pubkey),
            )
        }
    }

    @Test
    fun encode_matches_the_reference_for_pubkeys_with_leading_zero_bytes() {
        // Exercises the leading-'1' base58 path that a naive parity check on random bytes is
        // unlikely to hit.
        val pubkey = ByteArray(32) { if (it < 3) 0 else (it + 1).toByte() }
        assertEquals(ReferenceImpl.encode(pubkey), BittensorHelper.ss58Encode(pubkey))
    }

    @Test
    fun decode_recovers_exactly_the_pubkey_that_was_encoded() {
        for (seed in 1L..20L) {
            val pubkey = randomPubkey(seed)
            val address = BittensorHelper.ss58Encode(pubkey)
            assertArrayEquals("seed $seed", pubkey, BittensorHelper.ss58Decode(address))
        }
    }

    @Test
    fun decode_of_a_reference_encoded_address_matches_the_original_pubkey() {
        // Cross-checks the new decoder against addresses produced by the old encoder, not just
        // against itself.
        for (seed in 1L..20L) {
            val pubkey = randomPubkey(seed)
            val referenceAddress = ReferenceImpl.encode(pubkey)
            assertArrayEquals("seed $seed", pubkey, BittensorHelper.ss58Decode(referenceAddress))
        }
    }

    @Test
    fun decode_rejects_an_address_encoded_with_a_different_ss58_prefix() {
        // Behavior delta from #5667: the old decoder ignored the prefix byte's value, the new one
        // (via AnyAddress) validates it against SS58_PREFIX = 42.
        val pubkey = randomPubkey(99L)
        val polkadotAddress =
            wallet.core.jni
                .AnyAddress(
                    wallet.core.jni.PublicKey(pubkey, wallet.core.jni.PublicKeyType.ED25519),
                    wallet.core.jni.CoinType.POLKADOT,
                )
                .description()

        assertThrows(IllegalArgumentException::class.java) {
            BittensorHelper.ss58Decode(polkadotAddress)
        }
    }
}
