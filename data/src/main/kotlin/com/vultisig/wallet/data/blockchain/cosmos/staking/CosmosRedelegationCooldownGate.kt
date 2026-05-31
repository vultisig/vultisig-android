package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.time.Clock
import java.time.Instant

/**
 * The cosmos-sdk x/staking module rejects a `MsgBeginRedelegate` if an unexpired redelegation
 * record exists for the same `(src → *)` pair — this is the 21-day cooldown that prevents
 * validator-hopping. The rejection happens post-broadcast, after MPC has already signed.
 *
 * This gate evaluates `/cosmos/staking/v1beta1/delegators/{addr}/redelegations` BEFORE the SignDoc
 * is built. If any unfinished redelegation entry references the requested source validator, the
 * redelegate flow is blocked and the UI surfaces the earliest unlock date inline. Spec Risk 4:
 * "Don't surprise the user with an MPC burn".
 *
 * Port of iOS `CosmosRedelegationCooldownGate.swift` (vultisig-ios PR #4432).
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
                .filter { it.srcValidator == sourceValidator && it.completionTime.isAfter(now) }
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
