package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.time.Clock
import java.time.Instant

/**
 * The cosmos-sdk x/staking module rejects a `MsgBeginRedelegate` via
 * `HasReceivingRedelegation(delAddr, valSrcAddr)` — i.e. when the proposed SOURCE validator is the
 * DESTINATION of an existing unfinished redelegation by the same delegator. After `A → B`, a new
 * `B → C` is rejected with `ErrTransitiveRedelegation`. The 21-day cooldown is enforced
 * post-broadcast, after MPC has already signed.
 *
 * This gate evaluates `/cosmos/staking/v1beta1/delegators/{addr}/redelegations` BEFORE the SignDoc
 * is built. The filter looks at `dstValidator == sourceValidator` — i.e. the proposed source was
 * recently a destination — to mirror the chain's `HasReceivingRedelegation` rule. Spec Risk 4:
 * "Don't surprise the user with an MPC burn".
 *
 * Port of iOS `CosmosRedelegationCooldownGate.swift` (vultisig-ios PR #4432). The iOS port had the
 * filter flipped (was `srcValidator == sourceValidator`); patched here, upstream fix tracked
 * separately.
 */
object CosmosRedelegationCooldownGate {

    /**
     * Evaluates whether the source validator is currently under a redelegation cooldown. Pure
     * function over an LCD-fetched list.
     *
     * [now] is injected so the unit tests can pin the boundary deterministically — production
     * callers pass `Instant.now(Clock.systemUTC())`.
     */
    fun evaluate(
        sourceValidator: String,
        redelegations: List<CosmosRedelegationEntry>,
        now: Instant = Instant.now(Clock.systemUTC()),
    ): CosmosRedelegationCooldownState {
        val pending =
            redelegations
                .asSequence()
                .filter { it.dstValidator == sourceValidator && it.completionTime.isAfter(now) }
                .map { it.completionTime }
                .sorted()
                .toList()

        val earliest = pending.firstOrNull() ?: return CosmosRedelegationCooldownState.Available
        return CosmosRedelegationCooldownState.Blocked(unlocksAt = earliest)
    }
}

sealed class CosmosRedelegationCooldownState {
    /** Source validator has no pending redelegation cooldown — safe to begin a new redelegation. */
    object Available : CosmosRedelegationCooldownState()

    /** Source validator is under cooldown — surface [unlocksAt] inline. */
    data class Blocked(val unlocksAt: Instant) : CosmosRedelegationCooldownState()
}
