package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.models.KeyShare
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins down the keyshare-preservation invariant for reshare.
 *
 * The previous inline implementation in `KeygenViewModel.startKeygenDkls` had a stale-state bug: it
 * overwrote `vault.pubKeyECDSA` / `pubKeyEDDSA` BEFORE running the preservation filter, so the
 * filter compared NEW pubkeys against the existing list and never matched anything, silently
 * leaving stale OLD root keyshares in the vault. Extracting [mergeReshareKeyshares] as a pure
 * function turns that class of bug into a unit-test concern.
 */
class MergeReshareKeysharesTest {

    private val newEcdsa = KeyShare(pubKey = "new-ecdsa", keyShare = "share-ecdsa-new")
    private val newEddsa = KeyShare(pubKey = "new-eddsa", keyShare = "share-eddsa-new")

    private val oldEcdsa = KeyShare(pubKey = "old-ecdsa", keyShare = "share-ecdsa-old")
    private val oldEddsa = KeyShare(pubKey = "old-eddsa", keyShare = "share-eddsa-old")
    private val mldsa = KeyShare(pubKey = "mldsa-pk", keyShare = "share-mldsa")
    private val btcChain = KeyShare(pubKey = "btc-pk", keyShare = "share-btc")
    private val ethChain = KeyShare(pubKey = "eth-pk", keyShare = "share-eth")

    @Test
    fun `simple reshare returns only new ECDSA and EdDSA`() {
        val result =
            mergeReshareKeyshares(
                existing = listOf(oldEcdsa, oldEddsa),
                newEcdsa = newEcdsa,
                newEddsa = newEddsa,
                oldEcdsaPubKey = "old-ecdsa",
                oldEddsaPubKey = "old-eddsa",
                isReshare = true,
            )

        assertEquals(listOf(newEcdsa, newEddsa), result)
    }

    @Test
    fun `reshare on a vault with MLDSA preserves the MLDSA keyshare`() {
        val result =
            mergeReshareKeyshares(
                existing = listOf(oldEcdsa, oldEddsa, mldsa),
                newEcdsa = newEcdsa,
                newEddsa = newEddsa,
                oldEcdsaPubKey = "old-ecdsa",
                oldEddsaPubKey = "old-eddsa",
                isReshare = true,
            )

        assertEquals(3, result.size)
        assertEquals(setOf("new-ecdsa", "new-eddsa", "mldsa-pk"), result.map { it.pubKey }.toSet())
        assertTrue(result.contains(mldsa))
    }

    @Test
    fun `KeyImport vault reshare preserves all per-chain keyshares`() {
        val result =
            mergeReshareKeyshares(
                existing = listOf(oldEcdsa, oldEddsa, btcChain, ethChain),
                newEcdsa = newEcdsa,
                newEddsa = newEddsa,
                oldEcdsaPubKey = "old-ecdsa",
                oldEddsaPubKey = "old-eddsa",
                isReshare = true,
            )

        assertEquals(4, result.size)
        assertEquals(
            setOf("new-ecdsa", "new-eddsa", "btc-pk", "eth-pk"),
            result.map { it.pubKey }.toSet(),
        )
    }

    @Test
    fun `KeyImport vault with MLDSA preserves both MLDSA and per-chain keyshares`() {
        val result =
            mergeReshareKeyshares(
                existing = listOf(oldEcdsa, oldEddsa, mldsa, btcChain, ethChain),
                newEcdsa = newEcdsa,
                newEddsa = newEddsa,
                oldEcdsaPubKey = "old-ecdsa",
                oldEddsaPubKey = "old-eddsa",
                isReshare = true,
            )

        assertEquals(
            setOf("new-ecdsa", "new-eddsa", "mldsa-pk", "btc-pk", "eth-pk"),
            result.map { it.pubKey }.toSet(),
        )
    }

    @Test
    fun `non-reshare action drops everything except the freshly produced root shares`() {
        // KEYGEN, Migrate, KeyImport flows: the existing list is replaced (existing keyshares
        // belong to a previous vault and must not leak into the freshly-keyed one).
        val result =
            mergeReshareKeyshares(
                existing = listOf(oldEcdsa, oldEddsa, mldsa, btcChain),
                newEcdsa = newEcdsa,
                newEddsa = newEddsa,
                oldEcdsaPubKey = "old-ecdsa",
                oldEddsaPubKey = "old-eddsa",
                isReshare = false,
            )

        assertEquals(listOf(newEcdsa, newEddsa), result)
    }

    @Test
    fun `reshare on an empty vault returns only the new root shares`() {
        val result =
            mergeReshareKeyshares(
                existing = emptyList(),
                newEcdsa = newEcdsa,
                newEddsa = newEddsa,
                oldEcdsaPubKey = "",
                oldEddsaPubKey = "",
                isReshare = true,
            )

        assertEquals(listOf(newEcdsa, newEddsa), result)
    }

    @Test
    fun `reshare new pubkeys are placed first followed by preserved shares`() {
        val result =
            mergeReshareKeyshares(
                existing = listOf(mldsa, oldEcdsa, btcChain, oldEddsa),
                newEcdsa = newEcdsa,
                newEddsa = newEddsa,
                oldEcdsaPubKey = "old-ecdsa",
                oldEddsaPubKey = "old-eddsa",
                isReshare = true,
            )

        // First two slots must be the new root shares so downstream code that does
        // `vault.keyshares[0]` to find ECDSA stays consistent with keygen output.
        assertEquals(newEcdsa, result[0])
        assertEquals(newEddsa, result[1])
        // Order of preserved shares mirrors the input list order, with the dropped roots
        // skipped.
        assertEquals(listOf(mldsa, btcChain), result.drop(2))
    }

    @Test
    fun `reshare regression — filter must compare against OLD pubkeys, not NEW`() {
        // The original bug: the filter was run AFTER `vault.pubKeyECDSA` was overwritten with
        // the new pubkey, so it compared against `new-ecdsa` and never matched the existing
        // `old-ecdsa` entry — both old and new ECDSA shares ended up in the result. This test
        // would have caught it.
        val result =
            mergeReshareKeyshares(
                existing = listOf(oldEcdsa, oldEddsa),
                newEcdsa = newEcdsa,
                newEddsa = newEddsa,
                oldEcdsaPubKey = "old-ecdsa",
                oldEddsaPubKey = "old-eddsa",
                isReshare = true,
            )

        assertEquals(false, result.any { it.pubKey == "old-ecdsa" })
        assertEquals(false, result.any { it.pubKey == "old-eddsa" })
    }
}
