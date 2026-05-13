package com.vultisig.wallet.data.blockchain.tron

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class TronStakingTest {

    @Test
    fun `tronStakingMemo builds FREEZE BANDWIDTH memo`() {
        assertEquals(
            "FREEZE:BANDWIDTH",
            tronStakingMemo(TronStakingOperation.FREEZE, TronResourceType.BANDWIDTH),
        )
    }

    @Test
    fun `tronStakingMemo builds FREEZE ENERGY memo`() {
        assertEquals(
            "FREEZE:ENERGY",
            tronStakingMemo(TronStakingOperation.FREEZE, TronResourceType.ENERGY),
        )
    }

    @Test
    fun `tronStakingMemo builds UNFREEZE BANDWIDTH memo`() {
        assertEquals(
            "UNFREEZE:BANDWIDTH",
            tronStakingMemo(TronStakingOperation.UNFREEZE, TronResourceType.BANDWIDTH),
        )
    }

    @Test
    fun `tronStakingMemo builds UNFREEZE ENERGY memo`() {
        assertEquals(
            "UNFREEZE:ENERGY",
            tronStakingMemo(TronStakingOperation.UNFREEZE, TronResourceType.ENERGY),
        )
    }

    @Test
    fun `TRON_STAKING_MEMO_REGEX matches all four valid combinations`() {
        for (op in TronStakingOperation.entries) {
            for (resource in TronResourceType.entries) {
                val memo = tronStakingMemo(op, resource)
                assertTrue(
                    TRON_STAKING_MEMO_REGEX.matches(memo),
                    "expected regex to match generated memo $memo",
                )
            }
        }
    }

    @Test
    fun `TRON_STAKING_MEMO_REGEX rejects non-staking memos`() {
        listOf(
                "",
                "FREEZE",
                "FREEZE:",
                ":BANDWIDTH",
                "freeze:bandwidth", // wrong case
                "FREEZE:BANDWIDTH ", // trailing whitespace
                " FREEZE:BANDWIDTH",
                "FREEZE:BANDWIDTH:EXTRA",
                "FREEZE:UNKNOWN",
                "BURN:BANDWIDTH",
                "user memo",
            )
            .forEach { memo ->
                assertFalse(TRON_STAKING_MEMO_REGEX.matches(memo), "expected regex to reject $memo")
            }
    }
}
