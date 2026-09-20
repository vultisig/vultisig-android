package com.vultisig.wallet.data.chains.helpers

import org.junit.Assert.assertEquals
import org.junit.Test
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PrivateKey
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

/**
 * [Ss58] is the pure-Kotlin encoder the dApp transfer display uses for a recipient AccountId. Where
 * WalletCore's `AnyAddress` route works — a real Edwards point — the two must agree byte for byte;
 * where it does not, which is what [Ss58] exists for, this pins that it really does hand back `""`
 * rather than throwing, so the failure it replaces is on record.
 *
 * Runs under `app:connectedDebugAndroidTest` like [BittensorHelperSs58ParityTest].
 */
class Ss58ParityTest {

    private fun randomEdwardsPubkey(seed: Long): ByteArray {
        val seedBytes = ByteArray(32)
        java.security.SecureRandom.getInstance("SHA1PRNG")
            .apply { setSeed(seed) }
            .nextBytes(seedBytes)
        return PrivateKey(seedBytes).getPublicKeyEd25519().data()
    }

    private fun walletCoreSs58(accountId: ByteArray, prefix: Int): String =
        AnyAddress(PublicKey(accountId, PublicKeyType.ED25519), CoinType.POLKADOT, prefix)
            .description()

    @Test
    fun matches_wallet_core_for_edwards_keys_under_both_prefixes() {
        for (seed in 1L..20L) {
            val pubkey = randomEdwardsPubkey(seed)
            assertEquals("seed $seed", walletCoreSs58(pubkey, 0), Ss58.encode(pubkey, 0))
            assertEquals("seed $seed", walletCoreSs58(pubkey, 42), Ss58.encode(pubkey, 42))
            assertEquals("seed $seed", BittensorHelper.ss58Encode(pubkey), Ss58.encode(pubkey, 42))
        }
    }

    @Test
    fun spells_an_account_id_wallet_core_blanks() {
        // Bob's sr25519 dev key is a valid AccountId but not an ed25519 point.
        val bob = BittensorHelper.hexToBytes(BOB_SR25519)

        assertEquals("", walletCoreSs58(bob, 0))
        assertEquals("14E5nqKAp3oAJcmzgZhUD2RcptBeUBScxKHgJKU4HPNcKVf3", Ss58.encode(bob, 0))
    }

    private companion object {
        const val BOB_SR25519 = "8eaf04151687736326c9fea17e25fc5287613693c912909cb226aa4794f26a48"
    }
}
