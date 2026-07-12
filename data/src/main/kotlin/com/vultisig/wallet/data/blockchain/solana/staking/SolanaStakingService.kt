package com.vultisig.wallet.data.blockchain.solana.staking

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.models.SolanaProgramAccountJson
import com.vultisig.wallet.data.api.models.SolanaVoteAccountJson
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Read-side service for Solana native staking. Combines the raw JSON-RPC reads exposed by
 * [SolanaApi] (`getEpochInfo`, `getVoteAccounts`, `getProgramAccounts`) into the epoch-resolved
 * domain models the staking UI (later phases) consumes. Analogous to
 * [com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingService]; mirrors the iOS
 * foundation slice (vultisig-ios #4659). No UI and no signing live here.
 *
 * `getVoteAccounts` (the on-chain source of truth for stake/commission) is a large, slow-changing
 * response and is cached with a short TTL, as is epoch info. The wallet's own stake accounts are
 * NEVER cached — a stale position list would misreport what the user can deactivate or withdraw —
 * so [fetchStakeAccounts] always issues a fresh read.
 */
@Singleton
class SolanaStakingService @Inject constructor(private val solanaApi: SolanaApi) {

    /**
     * Clock + TTLs are `internal var` so tests can pin them; not constructor params because Dagger
     * ignores Kotlin default-valued arguments.
     */
    internal var clock: () -> Long = { System.currentTimeMillis() }
    internal var validatorsTtlMillis: Long = 10L * 60L * 1000L
    internal var epochTtlMillis: Long = 60L * 1000L

    private data class Cached<T>(val value: T, val fetchedAt: Long)

    // One mutex per cache (not a shared lock) so a slow getVoteAccounts can't block an unrelated
    // epoch read — and so the concurrent epoch + stake-accounts fan-out in fetchStakeAccounts stays
    // parallel. Each still serializes its own cache fill across the network call, which is fine at
    // these TTLs.
    private val validatorsMutex = Mutex()
    private val epochMutex = Mutex()
    private var cachedValidators: Cached<List<SolanaValidator>>? = null
    private var cachedEpoch: Cached<SolanaEpochInfo>? = null

    /**
     * The wallet's native stake accounts, one per delegation, with lifecycle state resolved against
     * the current epoch. Always a fresh read — never cached.
     *
     * @param ownerAddress base58 SOL address whose withdrawer-owned stake accounts to fetch
     */
    suspend fun fetchStakeAccounts(ownerAddress: String): List<SolanaStakeAccount> {
        // Epoch info and the stake-account read are independent — run them concurrently to save one
        // RPC round-trip on every positions-screen load.
        val (currentEpoch, accounts) =
            coroutineScope {
                val epoch = async { fetchEpochInfo()?.epoch }
                val stakeAccounts = async { solanaApi.getStakeAccounts(ownerAddress) }
                epoch.await() to stakeAccounts.await()
            }
        return accounts.map { it.toStakeAccount(currentEpoch) }
    }

    /**
     * All non-delinquent then delinquent validator vote accounts (the on-chain source of truth),
     * cached for [validatorsTtlMillis]. Empty when the RPC read fails.
     */
    suspend fun fetchValidators(): List<SolanaValidator> =
        validatorsMutex.withLock {
            cachedValidators?.let {
                if (isFresh(it.fetchedAt, validatorsTtlMillis)) return it.value
            }
            val result = solanaApi.getVoteAccounts() ?: return emptyList()
            val validators =
                (result.current.orEmpty().map { it.toValidator(delinquent = false) } +
                    result.delinquent.orEmpty().map { it.toValidator(delinquent = true) })
            cachedValidators = Cached(validators, clock())
            validators
        }

    /**
     * Current cluster epoch progress, cached for [epochTtlMillis]. Null when the RPC read fails.
     */
    suspend fun fetchEpochInfo(): SolanaEpochInfo? =
        epochMutex.withLock {
            cachedEpoch?.let { if (isFresh(it.fetchedAt, epochTtlMillis)) return it.value }
            val result = solanaApi.getEpochInfo() ?: return null
            val epoch =
                SolanaEpochInfo(
                    epoch = result.epoch,
                    slotIndex = result.slotIndex,
                    slotsInEpoch = result.slotsInEpoch,
                    absoluteSlot = result.absoluteSlot,
                )
            cachedEpoch = Cached(epoch, clock())
            epoch
        }

    private fun isFresh(fetchedAt: Long, ttlMillis: Long): Boolean = clock() - fetchedAt < ttlMillis

    private fun SolanaVoteAccountJson.toValidator(delinquent: Boolean): SolanaValidator =
        SolanaValidator(
            votePubkey = votePubkey,
            nodePubkey = nodePubkey,
            commission = commission,
            activatedStake = activatedStake,
            delinquent = delinquent,
        )

    private fun SolanaProgramAccountJson.toStakeAccount(currentEpoch: Long?): SolanaStakeAccount {
        val delegation = account.data?.parsed?.info?.stake?.delegation
        val meta = account.data?.parsed?.info?.meta
        val rentExemptReserve = meta?.rentExemptReserve?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val delegatedStake = delegation?.stake?.toBigIntegerOrNull() ?: BigInteger.ZERO
        // u64::MAX (the "not deactivating" sentinel) overflows Long, so toLongOrNull yields null —
        // exactly the "not set" shape state derivation expects.
        val activationEpoch = delegation?.activationEpoch?.toLongOrNull()
        val deactivationEpoch = delegation?.deactivationEpoch?.toLongOrNull()
        return SolanaStakeAccount(
            stakePubkey = pubkey,
            voter = delegation?.voter,
            lamports = account.lamports,
            delegatedStake = delegatedStake,
            rentExemptReserve = rentExemptReserve,
            activationEpoch = activationEpoch,
            deactivationEpoch = deactivationEpoch,
            state =
                deriveStakeState(
                    hasDelegation = delegation != null,
                    activationEpoch = activationEpoch,
                    deactivationEpoch = deactivationEpoch,
                    currentEpoch = currentEpoch,
                ),
        )
    }

    internal companion object {
        /**
         * Pure resolution of a stake account's lifecycle state from its delegation epochs and the
         * current cluster epoch. Extracted (and `internal`) so it can be unit-tested directly — it
         * gates withdraw / finish-move availability, and callers pass a `deactivationEpoch` that is
         * null when the on-chain `u64::MAX` "not deactivating" sentinel overflowed `Long`.
         */
        internal fun deriveStakeState(
            hasDelegation: Boolean,
            activationEpoch: Long?,
            deactivationEpoch: Long?,
            currentEpoch: Long?,
        ): SolanaStakeState {
            if (!hasDelegation) return SolanaStakeState.NotDelegated
            // Without a current epoch we can't resolve warmup/cooldown; report Active for a live
            // delegation and Deactivating once deactivation has been requested.
            if (currentEpoch == null) {
                return if (deactivationEpoch != null) SolanaStakeState.Deactivating
                else SolanaStakeState.Active
            }
            if (deactivationEpoch != null) {
                return if (currentEpoch > deactivationEpoch) SolanaStakeState.Inactive
                else SolanaStakeState.Deactivating
            }
            return if (activationEpoch != null && currentEpoch > activationEpoch)
                SolanaStakeState.Active
            else SolanaStakeState.Activating
        }
    }
}
