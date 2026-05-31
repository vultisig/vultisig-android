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
    val pendingReward: BigDecimal,
    /**
     * Fractional APY (`0.05` = 5%). Always `null` in this PR until [CosmosStakingAPYResolver] is
     * ported. The view layer hides the APY row when `null`, matching iOS behavior under chain-APY
     * fan-out failure.
     */
    val apyPercent: BigDecimal? = null,
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
