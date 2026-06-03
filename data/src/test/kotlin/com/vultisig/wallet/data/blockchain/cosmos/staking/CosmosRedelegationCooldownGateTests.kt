package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
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
    fun `transitive redelegation (src was recent dst) is Blocked with unlocksAt`() {
        // After `srcB -> srcA`, redelegating from srcA is blocked — cosmos-sdk
        // `HasReceivingRedelegation(delAddr, srcA)` returns true because srcA was the destination.
        val redelegations = listOf(CosmosRedelegationEntry(srcB, srcA, fiveDaysFromNow))
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
    fun `Blocked returns the EARLIEST unlock when multiple entries target src as dst`() {
        val redelegations =
            listOf(
                CosmosRedelegationEntry(srcB, srcA, tenDaysFromNow),
                CosmosRedelegationEntry(dstA, srcA, fiveDaysFromNow),
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
    fun `outgoing redelegation FROM src does NOT block (chain allows multiple from same src)`() {
        // The chain only blocks new `B -> C` when B was a recent DST. An active `A -> B` does NOT
        // prevent another `A -> C` (different dst). The gate must not over-block.
        val redelegations = listOf(CosmosRedelegationEntry(srcA, dstB, fiveDaysFromNow))
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
        val redelegations = listOf(CosmosRedelegationEntry(srcB, srcA, tenDaysAgo))
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
        val redelegations = listOf(CosmosRedelegationEntry(srcB, srcA, now))
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

    @Test
    fun `hasReachedMaxEntries true once the pair hits the cap`() {
        // MAX_ENTRIES active entries for the SAME (src, dst) pair → chain rejects a further
        // MsgBeginRedelegate with ErrMaxRedelegationEntries.
        val entries =
            List(CosmosStakingConfig.MAX_ENTRIES) {
                CosmosRedelegationEntry(srcA, dstA, fiveDaysFromNow)
            }
        assertTrue(
            CosmosRedelegationCooldownGate.hasReachedMaxEntries(
                sourceValidator = srcA,
                destinationValidator = dstA,
                redelegations = entries,
                now = now,
            )
        )
    }

    @Test
    fun `hasReachedMaxEntries counts only the matching pair and ignores expired`() {
        val entries = buildList {
            // Below the cap for (srcA, dstA): cap-1 active + 1 expired (ignored).
            repeat(CosmosStakingConfig.MAX_ENTRIES - 1) {
                add(CosmosRedelegationEntry(srcA, dstA, fiveDaysFromNow))
            }
            add(CosmosRedelegationEntry(srcA, dstA, tenDaysAgo))
            // A different dst should not count toward (srcA, dstA).
            add(CosmosRedelegationEntry(srcA, dstB, fiveDaysFromNow))
        }
        assertFalse(
            CosmosRedelegationCooldownGate.hasReachedMaxEntries(
                sourceValidator = srcA,
                destinationValidator = dstA,
                redelegations = entries,
                now = now,
            )
        )
    }
}
