package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.math.BigDecimal
import java.time.Instant

/**
 * Per-validator row in the LUNA / LUNC DeFi stake segment. Built by the positions view-model from
 * `CosmosDelegation` joined against `CosmosValidator` metadata, `CosmosDelegatorRewards`, and
 * `CosmosUnbondingDelegation`. The row carries everything the per-validator card needs to render:
 * staked amount, pending reward, validator status badge (active / churned out), per-validator APY,
 * Keybase avatar, and the earliest non-expired unbonding-completion timestamp (when set, Move is
 * disabled and the row footers an "Unlocks {date}" microcopy; Unstake remains available so the user
 * can always exit a position).
 *
 * Mirrors iOS `CosmosStakePositionRow` (vultisig-ios PR #4432).
 */
data class CosmosStakePositionRow(
    val validatorAddress: String,
    val validatorMoniker: String,
    /**
     * Keybase identity advertised by the validator — used to swap in the remote avatar in
     * `validatorAvatar(for:)`. `null` when the validator omits the field or when the validators
     * query fell back to the "Churned Out" default.
     */
    val validatorIdentity: String?,
    val stakedAmount: BigDecimal,
    /**
     * Pre-formatted fiat value of [stakedAmount] (`stakedAmount × spot price`) in the user's
     * currency — e.g. `"$0.35"`. Empty until the positions view-model resolves the price; a price
     * cache miss formats zero rather than blocking the row.
     */
    val stakedFiatDisplay: String = "",
    val pendingReward: BigDecimal,
    /**
     * Fractional APY (`0.05` = 5%). Populated by [CosmosStakingAPYResolver.computeValidatorAPY]
     * when the chain APY fan-out succeeds (or via [CosmosStakingAPYResolver.baselineFallback] for
     * LUNA). The view layer hides the APY row when `null`, matching iOS behavior under chain-APY
     * fan-out failure.
     */
    val apyPercent: BigDecimal? = null,
    /**
     * Resolved Keybase avatar URL for the validator, or `null` when the identity is missing or the
     * Keybase lookup didn't find an avatar. The view layer renders a deterministic monogram avatar
     * when this is `null`.
     */
    val validatorAvatarUrl: String? = null,
    val validatorStatus: ValidatorStatus,
    /**
     * Earliest non-expired unbonding completion timestamp for the validator, or `null` when there
     * are no pending unbondings. When non-null, the row renders an "Unlocks {date}" footer beneath
     * the action buttons.
     */
    val pendingUnbondingUnlockDate: Instant?,
    /**
     * Count of non-expired unbonding entries for this validator. cosmos-sdk allows up to
     * [CosmosStakingConfig.MAX_ENTRIES] concurrent unbonding entries per (delegator, validator)
     * pair, so a partial unstake is legal until that cap is hit — Unstake gates on
     * [maxUnbondingEntriesReached], NOT on the mere presence of a pending unbonding.
     */
    val pendingUnbondingEntryCount: Int = 0,
) {
    /**
     * True once the validator has [CosmosStakingConfig.MAX_ENTRIES] active unbonding entries — the
     * chain would reject a further `MsgUndelegate` with `ErrMaxUnbondingDelegationEntries`, so the
     * UI blocks Unstake at this point only (not while any single unbonding is merely pending).
     */
    val maxUnbondingEntriesReached: Boolean
        get() = pendingUnbondingEntryCount >= CosmosStakingConfig.MAX_ENTRIES

    enum class ValidatorStatus {
        Active,
        ChurnedOut,
    }
}
