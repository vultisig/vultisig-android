package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.Serializable

/** Top-level GraphQL data node for RUJI account queries. */
@Serializable data class RootData(val node: AccountNode?)

/** GraphQL account node containing merge and staking position data. */
@Serializable data class AccountNode(val merge: MergeInfo?, val stakingV2: List<StakingV2>?)

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

/**
 * A single RUJI staking-v2 position.
 *
 * A Rujira staking account holds two independent positions at once: the bonded ("standard") one,
 * which earns manually-claimable USDC revenue, and the auto-compounding one, whose revenue buys
 * more of the bond token into the position and is receipted by an sRUJI share token.
 */
@Serializable
data class StakingV2(
    val account: String,
    val bonded: Bonded,
    val liquidSize: LiquidSize? = null,
    val liquidShares: LiquidShares? = null,
    val pendingRevenue: PendingRevenue?,
    val pool: Pool?,
)

/** Bonded (staked) token amount and asset for a staking position. */
@Serializable data class Bonded(val amount: String? = null, val asset: Asset? = null)

/**
 * The auto-compounding position's value, denominated in the *bond* token (RUJI) rather than in
 * receipt shares. This is the displayable amount: the share price rises as revenue compounds, so
 * the raw share count understates the position.
 */
@Serializable data class LiquidSize(val amount: String? = null)

/**
 * The raw sRUJI receipt share count backing the auto-compounding position — equal to the vault's
 * on-chain `x/staking-x/ruji` balance. Not a display value; it sizes the `liquid.unbond`
 * redemption, which is funded in shares.
 */
@Serializable data class LiquidShares(val amount: String? = null)

/** Pending revenue amount and asset accrued by a staking position. */
@Serializable data class PendingRevenue(val amount: String, val asset: Asset)

/** A generic asset reference with optional metadata. */
@Serializable data class Asset(val metadata: Metadata? = null)

/** Pool-level summary statistics, including APR. */
@Serializable data class Summary(val apr: Apr? = null)

/**
 * A staking pool's annual percentage rate.
 *
 * [value] is a Rujira bigint scalar, not a percentage: decimal quantities are carried at 12 decimal
 * places, so `"11623890337"` is a fractional rate of `0.011623890337` — 1.16%. Reading it as a bare
 * double would render that as 11623890337.00%.
 */
@Serializable
data class Apr(
    val value: String? = null,
    /** `AVAILABLE` | `NOT_APPLICABLE` | `SOON`. Anything but `AVAILABLE` publishes no rate. */
    val status: String? = null,
) {
    /**
     * The fractional rate (`0.0116` for 1.16%), or null when the pool publishes no usable one.
     *
     * Null rather than zero on every failure — an absent rate hides the APR row, where a zero would
     * assert the position earns nothing.
     */
    val fractionalRate: Double?
        get() {
            if (status != null && status != APR_STATUS_AVAILABLE) return null
            val raw = value?.toBigDecimalOrNull() ?: return null
            return raw.movePointLeft(APR_DECIMALS).toDouble()
        }

    private companion object {
        const val APR_STATUS_AVAILABLE = "AVAILABLE"
        const val APR_DECIMALS = 12
    }
}
