package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

/**
 * Pins the iOS cooldown-gate semantics on Android. Mirrors the iOS test suite — same fixtures, same
 * boundary conditions.
 */
class CosmosRedelegationCooldownGateTests {

    private val now: Instant = Instant.parse("2026-06-01T00:00:00Z")
    private val fiveDaysFromNow: Instant = Instant.parse("2026-06-06T00:00:00Z")
    private val tenDaysFromNow: Instant = Instant.parse("2026-06-11T00:00:00Z")
    private val tenDaysAgo: Instant = Instant.parse("2026-05-22T00:00:00Z")

    private val srcA = "terravaloper1aaa"
    private val srcB = "terravaloper1bbb"
    private val dstA = "terravaloper1ccc"
    private val dstB = "terravaloper1ddd"

    @Test
    fun `clear path is Available`() {
        val redelegations = listOf(CosmosRedelegationEntry(srcB, dstB, fiveDaysFromNow))
        val state =
            CosmosRedelegationCooldownGate.evaluate(
                sourceValidator = srcA,
                redelegations = redelegations,
                now = now,
            )
        assertIs<CosmosRedelegationCooldownState.Available>(state)
    }

    @Test
    fun `outgoing from src already in cooldown returns Blocked with unlocksAt`() {
        val redelegations = listOf(CosmosRedelegationEntry(srcA, dstB, fiveDaysFromNow))
        val state =
            CosmosRedelegationCooldownGate.evaluate(
                sourceValidator = srcA,
                redelegations = redelegations,
                now = now,
            )
        val blocked = assertIs<CosmosRedelegationCooldownState.Blocked>(state)
        assertEquals(fiveDaysFromNow, blocked.unlocksAt)
    }

    @Test
    fun `Blocked returns the EARLIEST unlock when multiple entries exist`() {
        val redelegations =
            listOf(
                CosmosRedelegationEntry(srcA, dstB, tenDaysFromNow),
                CosmosRedelegationEntry(srcA, dstA, fiveDaysFromNow),
            )
        val state =
            CosmosRedelegationCooldownGate.evaluate(
                sourceValidator = srcA,
                redelegations = redelegations,
                now = now,
            )
        val blocked = assertIs<CosmosRedelegationCooldownState.Blocked>(state)
        assertEquals(fiveDaysFromNow, blocked.unlocksAt)
    }

    @Test
    fun `entry whose src targets a DIFFERENT validator does not block`() {
        // iOS only checks src == sourceValidator. Unlike my earlier (over-conservative) port,
        // an entry where dst == sourceValidator does NOT trip the gate — the chain only rejects
        // when the same validator is the SOURCE of a pending redelegation.
        val redelegations = listOf(CosmosRedelegationEntry(srcB, srcA, fiveDaysFromNow))
        val state =
            CosmosRedelegationCooldownGate.evaluate(
                sourceValidator = srcA,
                redelegations = redelegations,
                now = now,
            )
        assertIs<CosmosRedelegationCooldownState.Available>(state)
    }

    @Test
    fun `expired entries are ignored`() {
        val redelegations = listOf(CosmosRedelegationEntry(srcA, dstA, tenDaysAgo))
        val state =
            CosmosRedelegationCooldownGate.evaluate(
                sourceValidator = srcA,
                redelegations = redelegations,
                now = now,
            )
        assertIs<CosmosRedelegationCooldownState.Available>(state)
    }

    @Test
    fun `entry exactly at now boundary is treated as expired`() {
        // `> now` per iOS — completionTime == now is past, not pending.
        val redelegations = listOf(CosmosRedelegationEntry(srcA, dstA, now))
        val state =
            CosmosRedelegationCooldownGate.evaluate(
                sourceValidator = srcA,
                redelegations = redelegations,
                now = now,
            )
        assertIs<CosmosRedelegationCooldownState.Available>(state)
    }

    @Test
    fun `empty redelegation list is Available`() {
        val state =
            CosmosRedelegationCooldownGate.evaluate(
                sourceValidator = srcA,
                redelegations = emptyList(),
                now = now,
            )
        assertIs<CosmosRedelegationCooldownState.Available>(state)
    }
}
