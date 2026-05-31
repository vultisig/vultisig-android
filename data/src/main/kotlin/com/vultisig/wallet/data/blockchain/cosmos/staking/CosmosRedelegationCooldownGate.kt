package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Cosmos-SDK x/staking forbids more than one in-flight redelegation per (delegator → dst, dst →
 * src) edge inside the unbonding period (21 days for Terra). Submitting a second redelegation that
 * involves either endpoint of an existing entry returns `ErrTransitiveRedelegation` on-chain.
 *
 * Port of iOS `CosmosRedelegationCooldownGate.swift` (vultisig-ios PR #4432). The UI consults this
 * gate when the user picks src + dst on the redelegate form and disables `validForm` when a hit
 * exists.
 */
object CosmosRedelegationCooldownGate {

    data class CooldownHit(
        val srcValidator: String,
        val dstValidator: String,
        val completionTime: Instant,
    )

    /**
     * Returns the soonest cooldown hit that blocks a redelegation from [validatorSrcAddress] to
     * [validatorDstAddress], or `null` if the path is clear. A hit is recorded when:
     * - an existing entry already redelegates FROM `src` (chain rejects a second outgoing from the
     *   same source while one is in cooldown), OR
     * - an existing entry redelegates TO `src` (chain rejects redelegation away from a validator
     *   that just received stake from elsewhere — "transitive redelegation"), OR
     * - an existing entry already redelegates FROM `src` TO `dst` (exact-path duplicate).
     *
     * Hits are filtered by [now]; expired entries are ignored.
     */
    fun cooldownFor(
        validatorSrcAddress: String,
        validatorDstAddress: String,
        redelegations: List<CosmosRedelegationEntry>,
        now: Instant = Instant.now(Clock.systemUTC()),
    ): CooldownHit? {
        return redelegations
            .asSequence()
            .filter { it.completionTime.isAfter(now) }
            .filter { entry ->
                entry.srcValidator == validatorSrcAddress ||
                    entry.dstValidator == validatorSrcAddress ||
                    (entry.srcValidator == validatorSrcAddress &&
                        entry.dstValidator == validatorDstAddress)
            }
            .minByOrNull { it.completionTime }
            ?.let { entry ->
                CooldownHit(
                    srcValidator = entry.srcValidator,
                    dstValidator = entry.dstValidator,
                    completionTime = entry.completionTime,
                )
            }
    }

    /**
     * Human-readable lock duration helper — returns the days remaining until [completionTime].
     * Caller renders the microcopy ("Locked for N days" / "Try again after MMM dd"); this just does
     * the arithmetic.
     */
    fun daysUntil(completionTime: Instant, now: Instant = Instant.now(Clock.systemUTC())): Long {
        val between = Duration.between(now, completionTime)
        if (between.isNegative || between.isZero) return 0
        // Round up — a 6h remainder shows as "1 day left", matching Maya/Thor.
        return (between.toHours() + 23) / 24
    }
}
