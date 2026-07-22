package com.vultisig.wallet.data.blockchain.solana.staking

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Domain models for the Solana native-staking read layer — the mapped, epoch-resolved shapes the
 * (later) view-model layer consumes. Raw JSON-RPC wire types live in
 * `com.vultisig.wallet.data.api.models`. Mirrors the iOS foundation slice (vultisig-ios #4659).
 */

/**
 * Lifecycle of a delegated stake account, derived by comparing the account's activation /
 * deactivation epochs against the current epoch.
 *
 * Solana stake activates at the next epoch boundary (~2 days) and deactivation cools for ~1 epoch
 * before the lamports can be withdrawn — that gating is what these states encode.
 */
enum class SolanaStakeState {
    /** Delegated but not yet fully warmed up (delegated this epoch). */
    Activating,
    /** Fully active and earning (auto-compounding) rewards. */
    Active,
    /** Deactivation requested; still cooling down this epoch. */
    Deactivating,
    /** Fully cooled down — lamports can be withdrawn. */
    Inactive,
    /** Stake account exists but carries no delegation (e.g. freshly initialized). */
    NotDelegated,
}

/**
 * A single native stake account owned by the wallet. One stake account maps to exactly one
 * validator, and a wallet may hold N of them — so the UI renders one row per account, not per
 * validator.
 *
 * @property stakePubkey base58 address of the stake account itself
 * @property voter base58 vote-account address of the validator this account delegates to, or null
 *   when [state] is [SolanaStakeState.NotDelegated]
 * @property lamports total lamports held by the account (delegated stake + rent-exempt reserve)
 * @property delegatedStake lamports actively delegated (excludes the rent-exempt reserve)
 * @property rentExemptReserve lamports reserved for rent exemption; not withdrawable while
 *   delegated
 * @property activationEpoch epoch the delegation activated in
 * @property deactivationEpoch epoch deactivation was requested in, or null when not deactivating
 *   (the on-chain `u64::MAX` sentinel)
 * @property state epoch-resolved lifecycle state
 */
data class SolanaStakeAccount(
    val stakePubkey: String,
    val voter: String?,
    val lamports: BigInteger,
    val delegatedStake: BigInteger,
    val rentExemptReserve: BigInteger,
    val activationEpoch: Long?,
    val deactivationEpoch: Long?,
    val state: SolanaStakeState,
)

/**
 * A validator vote account as reported by `getVoteAccounts` — the on-chain source of truth for
 * stake and commission. Name / logo / APY are layered on top by the (Phase 2) metadata provider;
 * this model carries only what the chain itself knows.
 *
 * @property votePubkey base58 vote-account address (the delegation target)
 * @property nodePubkey base58 identity of the validator node
 * @property commission commission percentage the validator takes (0..100)
 * @property activatedStake total lamports currently delegated to this validator
 * @property delinquent true when the validator is in the `delinquent` bucket (not voting)
 */
data class SolanaValidator(
    val votePubkey: String,
    val nodePubkey: String,
    val commission: Int,
    val activatedStake: BigInteger,
    val delinquent: Boolean,
)

/**
 * Off-chain display enrichment for a validator — name, logo, APY, quality score — layered on top of
 * the on-chain [SolanaValidator] by a [ValidatorMetadataProvider]. Every field is nullable: the
 * provider degrades gracefully on outage, so a validator with no metadata renders from on-chain
 * data alone (truncated vote pubkey + commission, no logo). Mirrors the iOS metadata seam
 * (vultisig-ios #4660).
 *
 * @property name human-readable validator name, or null when unknown
 * @property logoUrl absolute logo URL, or null to fall back to a deterministic monogram avatar
 * @property apyEstimate estimated APY as a fraction (e.g. `0.0572` for 5.72%), or null when unknown
 * @property score 0..100 quality score from the metadata source, or null when unknown
 */
data class ValidatorMetadata(
    val name: String? = null,
    val logoUrl: String? = null,
    val apyEstimate: BigDecimal? = null,
    val score: Int? = null,
)

/**
 * Current cluster epoch progress from `getEpochInfo`. Drives stake-state derivation and the
 * activation / cooldown copy shown on the DeFi tab.
 *
 * @property epoch current epoch number
 * @property slotIndex slot offset within the current epoch
 * @property slotsInEpoch total slots in the current epoch
 * @property absoluteSlot current absolute slot across all epochs
 */
data class SolanaEpochInfo(
    val epoch: Long,
    val slotIndex: Long,
    val slotsInEpoch: Long,
    val absoluteSlot: Long,
) {
    /**
     * Fraction (0.0..1.0) of the current epoch elapsed; 0.0 when [slotsInEpoch] is non-positive.
     */
    val progress: Double
        get() = if (slotsInEpoch > 0) slotIndex.toDouble() / slotsInEpoch.toDouble() else 0.0
}
