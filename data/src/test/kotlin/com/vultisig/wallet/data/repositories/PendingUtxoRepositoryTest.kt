package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.UtxoInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Sequential Dash sends: the confirmed-only UTXO sources keep offering an outpoint the previous
 * (broadcast, unconfirmed) transaction already spent, which Dash rejects as `tx-txlock-conflict`
 * after the signing ceremony. See issue #5453.
 */
internal class PendingUtxoRepositoryTest {

    @Test
    fun `pending spend hides the reused input and offers only the unconfirmed change`() {
        val repository = PendingUtxoRepository()
        repository.record(Chain.Dash, FIRST_SEND_RAW_TX, FIRST_SEND_HASH)
        val confirmed = listOf(UtxoInfo(hash = FUNDING_HASH, amount = 6_000, index = 1u))

        // The payee output of the pending send is not ours, so only its change comes back.
        assertEquals(
            listOf(UtxoInfo(hash = FIRST_SEND_HASH, amount = 5_000, index = 1u)),
            repository.applyTo(Chain.Dash, confirmed, ownScriptPubKeyHex = OWN_SCRIPT),
        )
        // Without a lock script to match, the exclusion still applies but nothing is offered.
        assertTrue(repository.applyTo(Chain.Dash, confirmed, ownScriptPubKeyHex = null).isEmpty())
    }

    @Test
    fun `other utxo chains keep the confirmed-only view`() {
        val repository = PendingUtxoRepository()
        repository.record(Chain.Dash, FIRST_SEND_RAW_TX, FIRST_SEND_HASH)

        val confirmed = listOf(UtxoInfo(hash = FUNDING_HASH, amount = 6_000, index = 1u))

        assertEquals(
            confirmed,
            repository.applyTo(Chain.Bitcoin, confirmed, ownScriptPubKeyHex = OWN_SCRIPT),
        )
    }

    @Test
    fun `record is dropped once one of its outputs is confirmed`() {
        val repository = PendingUtxoRepository()
        repository.record(Chain.Dash, FIRST_SEND_RAW_TX, FIRST_SEND_HASH)

        val confirmed = listOf(UtxoInfo(hash = FIRST_SEND_HASH, amount = 5_000, index = 1u))

        assertEquals(confirmed, repository.applyTo(Chain.Dash, confirmed, OWN_SCRIPT))
        assertTrue(repository.pendingSpends(Chain.Dash).isEmpty())
    }

    @Test
    fun `an expired record stops hiding its inputs`() {
        val repository = PendingUtxoRepository()
        repository.record(Chain.Dash, FIRST_SEND_RAW_TX, FIRST_SEND_HASH)

        val confirmed = listOf(UtxoInfo(hash = FUNDING_HASH, amount = 6_000, index = 1u))
        val afterTtl = System.currentTimeMillis() + THIRTY_ONE_MINUTES_MILLIS

        assertEquals(
            confirmed,
            repository.applyTo(
                chain = Chain.Dash,
                confirmedUtxos = confirmed,
                ownScriptPubKeyHex = OWN_SCRIPT,
                nowMillis = afterTtl,
            ),
        )
    }

    @Test
    fun `a chained send does not re-offer the change it already spent`() {
        val repository = PendingUtxoRepository()
        repository.record(Chain.Dash, FIRST_SEND_RAW_TX, FIRST_SEND_HASH)
        repository.record(Chain.Dash, SECOND_SEND_RAW_TX, SECOND_SEND_HASH)

        val spendable =
            repository.applyTo(
                chain = Chain.Dash,
                confirmedUtxos = listOf(UtxoInfo(hash = FUNDING_HASH, amount = 6_000, index = 1u)),
                ownScriptPubKeyHex = OWN_SCRIPT,
            )

        assertEquals(
            listOf(UtxoInfo(hash = SECOND_SEND_HASH, amount = 3_000, index = 1u)),
            spendable,
        )
    }

    private companion object {
        const val THIRTY_ONE_MINUTES_MILLIS = 31L * 60 * 1000

        const val FUNDING_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val FIRST_SEND_HASH =
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        const val SECOND_SEND_HASH =
            "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"

        // P2PKH lock scripts; the "cc" key hash is the vault's own address.
        const val PAYEE_SCRIPT = "76a914bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb88ac"
        const val OWN_SCRIPT = "76a914cccccccccccccccccccccccccccccccccccccccc88ac"

        // Spends FUNDING_HASH:1 (6000), paying 1000 to the payee and 5000 back as change.
        val FIRST_SEND_RAW_TX =
            "02000000" +
                "01" +
                FUNDING_HASH +
                "01000000" +
                "00" +
                "ffffffff" +
                "02" +
                "e803000000000000" +
                "19" +
                PAYEE_SCRIPT +
                "8813000000000000" +
                "19" +
                OWN_SCRIPT +
                "00000000"

        // Spends the still-unconfirmed change of the first send, leaving 3000 as new change.
        val SECOND_SEND_RAW_TX =
            "02000000" +
                "01" +
                FIRST_SEND_HASH +
                "01000000" +
                "00" +
                "ffffffff" +
                "02" +
                "d007000000000000" +
                "19" +
                PAYEE_SCRIPT +
                "b80b000000000000" +
                "19" +
                OWN_SCRIPT +
                "00000000"
    }
}
