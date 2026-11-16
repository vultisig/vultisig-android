package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.ui.utils.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeysignProgressTest {

    @Test
    fun `single-message signing keeps the phase progress`() {
        assertEquals(0.33f, keysignProgress(KeysignState.KeysignECDSA, 0, 1))
        assertEquals(0.33f, keysignProgress(KeysignState.KeysignECDSA, 1, 1))
        assertEquals(0.66f, keysignProgress(KeysignState.KeysignEdDSA, 1, 1))
        assertEquals(0.66f, keysignProgress(KeysignState.KeysignMLDSA, 0, 0))
    }

    @Test
    fun `multi-message signing starts at the phase progress`() {
        assertEquals(0.33f, keysignProgress(KeysignState.KeysignECDSA, 0, 66))
        assertEquals(0.66f, keysignProgress(KeysignState.KeysignEdDSA, 0, 10))
    }

    @Test
    fun `multi-message signing moves after every signature and never backwards`() {
        val total = 66
        val values = (0..total).map { keysignProgress(KeysignState.KeysignECDSA, it, total) }

        values.zipWithNext().forEach { (before, after) -> assertTrue(after > before) }
    }

    @Test
    fun `multi-message signing reaches 100 percent only once every signature is done`() {
        val total = 10

        (0 until total).forEach {
            assertTrue(keysignProgress(KeysignState.KeysignECDSA, it, total) < 1f)
        }
        assertEquals(1f, keysignProgress(KeysignState.KeysignECDSA, total, total))
    }

    @Test
    fun `waiting for a peer keeps the signed fraction`() {
        val signing = keysignProgress(KeysignState.KeysignECDSA, 4, 10)
        val waiting =
            keysignProgress(
                KeysignState.WaitingForPeer(listOf("peer"), signingProgress = 0.33f),
                4,
                10,
            )

        assertEquals(signing, waiting)
    }

    @Test
    fun `out-of-range counts are clamped`() {
        assertEquals(0.33f, keysignProgress(KeysignState.KeysignECDSA, -1, 10))
        assertEquals(1f, keysignProgress(KeysignState.KeysignECDSA, 12, 10))
    }

    @Test
    fun `many-inputs label shows from 10 signatures with the count`() {
        assertNull(KeysignUiState(totalMessageCount = 9).manyInputsCount)
        assertEquals(10, KeysignUiState(totalMessageCount = 10).manyInputsCount)
    }

    @Test
    fun `non-signing states ignore the signed count`() {
        assertEquals(0f, keysignProgress(KeysignState.CreatingInstance, 5, 10))
        assertEquals(
            1f,
            keysignProgress(KeysignState.KeysignFinished(TransactionStatus.Broadcasted), 5, 10),
        )
        assertEquals(
            0f,
            keysignProgress(KeysignState.Error(UiText.DynamicString("failed")), 5, 10),
        )
    }
}
