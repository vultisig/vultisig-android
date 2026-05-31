package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.math.BigDecimal

/**
 * Per-validator candidate for the LUNA / LUNC claim-rewards flow. Built from
 * `CosmosDelegatorRewards` joined against `CosmosValidator` metadata so the UI can render the
 * validator moniker (not just the truncated valoper address) alongside the pending reward.
 *
 * Mirrors the iOS `CosmosWithdrawRewardsCandidate` model (vultisig-ios PR #4432). [pendingReward]
 * is the bond-denom-only sum in human units (already divided by `10^coin.decimals`).
 */
data class CosmosWithdrawRewardsCandidate(
    val validatorAddress: String,
    val validatorMoniker: String,
    val pendingReward: BigDecimal,
)
