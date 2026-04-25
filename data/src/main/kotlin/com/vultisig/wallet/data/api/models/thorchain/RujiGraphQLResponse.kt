package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

/** Top-level GraphQL data node for RUJI account queries. */
@Serializable data class RootData(val node: AccountNode?)

/** GraphQL account node containing merge and staking position data. */
@Serializable data class AccountNode(val merge: MergeInfo?, val stakingV2: List<StakingV2?>?)

/** Merge pool positions held by an account. */
@Serializable data class MergeInfo(val accounts: List<MergeAccount>)

/** A single MERGE pool position with pool info, size, and share count. */
@Serializable data class MergeAccount(val pool: Pool?, val size: Size?, val shares: String?)

/** A liquidity pool with its merge asset and optional summary data. */
@Serializable data class Pool(val mergeAsset: MergeAsset?, val summary: Summary? = null)

/** The merge asset within a pool, identified by its metadata. */
@Serializable data class MergeAsset(val metadata: Metadata?)

/** Asset metadata containing the token symbol. */
@Serializable data class Metadata(val symbol: String?)

/** The size (amount) of a position in a pool. */
@Serializable data class Size(val amount: String?)

/** A single RUJI staking-v2 position with bonded amount, pending revenue, and pool info. */
@Serializable
data class StakingV2(
    val account: String,
    val bonded: Bonded,
    val pendingRevenue: PendingRevenue?,
    val pool: Pool?,
)

/** Bonded (staked) token amount and asset for a staking position. */
@Serializable data class Bonded(val amount: String, val asset: Asset)

/** Pending revenue amount and asset accrued by a staking position. */
@Serializable data class PendingRevenue(val amount: String, val asset: Asset)

/** A generic asset reference with optional metadata. */
@Serializable data class Asset(val metadata: Metadata? = null)

/** Pool-level summary statistics, including APR. */
@Serializable data class Summary(val apr: Apr? = null)

/** Annual percentage rate value for a staking pool. */
@Serializable data class Apr(val value: String? = null)
