package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class CosmosRedelegationCooldownGateTests {

    private val now: Instant = Instant.parse("2026-06-01T00:00:00Z")
    private val fiveDaysFromNow: Instant = Instant.parse("2026-06-06T00:00:00Z")
    private val tenDaysAgo: Instant = Instant.parse("2026-05-22T00:00:00Z")

    private val srcA = "terravaloper1aaa"
    private val srcB = "terravaloper1bbb"
    private val dstA = "terravaloper1ccc"
    private val dstB = "terravaloper1ddd"

    @Test
    fun `clear path returns null`() {
        val redelegations = listOf(CosmosRedelegationEntry(srcB, dstB, fiveDaysFromNow))
        val hit =
            CosmosRedelegationCooldownGate.cooldownFor(
                validatorSrcAddress = srcA,
                validatorDstAddress = dstA,
                redelegations = redelegations,
                now = now,
            )
        assertNull(hit)
    }

    @Test
    fun `outgoing from src already in cooldown is blocked`() {
        val redelegations = listOf(CosmosRedelegationEntry(srcA, dstB, fiveDaysFromNow))
        val hit =
            CosmosRedelegationCooldownGate.cooldownFor(
                validatorSrcAddress = srcA,
                validatorDstAddress = dstA,
                redelegations = redelegations,
                now = now,
            )
        assertNotNull(hit)
        assertEquals(srcA, hit.srcValidator)
    }

    @Test
    fun `transitive redelegation from previous dst is blocked`() {
        // An entry that already targeted srcA as its destination — moving stake away from srcA
        // before that entry clears is a transitive redelegation, which the chain rejects.
        val redelegations = listOf(CosmosRedelegationEntry(srcB, srcA, fiveDaysFromNow))
        val hit =
            CosmosRedelegationCooldownGate.cooldownFor(
                validatorSrcAddress = srcA,
                validatorDstAddress = dstA,
                redelegations = redelegations,
                now = now,
            )
        assertNotNull(hit)
    }

    @Test
    fun `expired entries are ignored`() {
        val redelegations = listOf(CosmosRedelegationEntry(srcA, dstA, tenDaysAgo))
        val hit =
            CosmosRedelegationCooldownGate.cooldownFor(
                validatorSrcAddress = srcA,
                validatorDstAddress = dstA,
                redelegations = redelegations,
                now = now,
            )
        assertNull(hit)
    }

    @Test
    fun `daysUntil returns ceil for partial-day remainders`() {
        // 5 days + 6 hours rounds up to 6.
        val target = Instant.parse("2026-06-06T06:00:00Z")
        assertEquals(6, CosmosRedelegationCooldownGate.daysUntil(target, now))
    }

    @Test
    fun `daysUntil returns zero when already past`() {
        assertEquals(0, CosmosRedelegationCooldownGate.daysUntil(tenDaysAgo, now))
    }
}
