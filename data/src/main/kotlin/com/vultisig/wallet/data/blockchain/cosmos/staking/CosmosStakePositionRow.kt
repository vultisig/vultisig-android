package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.math.BigDecimal
import java.time.Instant

/**
 * Per-validator row in the LUNA / LUNC DeFi stake segment. Built by the positions view-model from
 * `CosmosDelegation` joined against `CosmosValidator` metadata, `CosmosDelegatorRewards`, and
 * `CosmosUnbondingDelegation`. The row carries everything the per-validator card needs to render:
 * staked amount, pending reward, validator status badge (active / churned out), and the earliest
 * non-expired unbonding-completion timestamp (when set, Unstake + Move are disabled and the row
 * footers an "Unlocks {date}" microcopy).
 *
 * Mirrors iOS `CosmosStakePositionRow` (vultisig-ios PR #4432).
 *
 * **APY is deferred**: iOS computes a per-validator APY via [CosmosStakingAPYResolver] (LCD fan-out
 * over mint inflation, staking pool, bank supply, distribution params). That resolver is not yet
 * ported on Android — [apyPercent] is left `null` here so the view layer hides the APY row the same
 * way iOS does when the chain APY data is unavailable.
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
     * are no pending unbondings. When non-null, the row disables Undelegate + Redelegate and
     * renders an "Unlocks {date}" footer beneath the action buttons.
     */
    val pendingUnbondingUnlockDate: Instant?,
) {
    enum class ValidatorStatus {
        Active,
        ChurnedOut,
    }
}
