package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainLiquidityProviderJson
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolJson
import com.vultisig.wallet.data.models.ThorChainPendingLpDeposit
import com.vultisig.wallet.data.utils.NetworkException
import java.io.IOException
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/** Mimir key for the number of blocks THORChain holds a half-deposit before refunding it. */
private const val PENDING_LIQUIDITY_AGE_LIMIT_MIMIR = "PENDINGLIQUIDITYAGELIMIT"

/**
 * `PendingLiquidityAgeLimit`'s base constant, used when mimir carries no override. 100,800 blocks
 * at THORChain's ~6s block time is roughly a week.
 */
private const val DEFAULT_PENDING_LIQUIDITY_AGE_LIMIT = 100_800L

interface GetThorChainPendingLpDepositsUseCase {
    /**
     * Finds the user's half-finished symmetric add-liquidity deposits.
     *
     * A symmetric add mints no LP units until both sides arrive, so these deposits are invisible to
     * every position lookup — including this app's own, which reads a unit-less record as "no
     * position". Unfound, they are simply refunded after `PendingLiquidityAgeLimit` with nothing
     * ever shown to the user.
     *
     * The scan starts from `/thorchain/pools`, whose `pending_inbound_*` fields say which pools
     * hold *anyone's* pending liquidity. Only those pools are then checked against the user's
     * address, so the usual cost is one request rather than one per pool.
     *
     * @param runeAddress the user's RUNE address — one side of every symmetric add, and the side
     *   thornode keys the record by.
     */
    suspend operator fun invoke(runeAddress: String): List<ThorChainPendingLpDeposit>
}

internal class GetThorChainPendingLpDepositsUseCaseImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : GetThorChainPendingLpDepositsUseCase {

    override suspend fun invoke(runeAddress: String): List<ThorChainPendingLpDeposit> {
        val candidates =
            try {
                thorChainApi.getPools().filter { it.holdsPendingLiquidity() }
            } catch (e: IOException) {
                Timber.w(e, "Failed to scan pools for pending liquidity")
                return emptyList()
            } catch (e: NetworkException) {
                Timber.w(e, "Failed to scan pools for pending liquidity")
                return emptyList()
            }
        if (candidates.isEmpty()) return emptyList()

        val found = coroutineScope {
            candidates
                .map { pool -> async { fetchPending(pool.asset, runeAddress) } }
                .awaitAll()
                .filterNotNull()
        }
        if (found.isEmpty()) return emptyList()

        return withRefundCountdown(found)
    }

    private fun ThorChainPoolJson.holdsPendingLiquidity(): Boolean =
        (pendingInboundRune.toBigIntegerOrNull()?.signum() ?: 0) > 0 ||
            (pendingInboundAsset.toBigIntegerOrNull()?.signum() ?: 0) > 0

    /**
     * Reads the user's LP record on [pool]. Looking it up by RUNE address alone is sufficient:
     * thornode keys every record by the RUNE address whenever the add carries one, and a symmetric
     * add always does — so an asset-address lookup can only ever return what this one already did.
     */
    private suspend fun fetchPending(pool: String, runeAddress: String): PendingFetch? =
        try {
            thorChainApi.getLiquidityProvider(pool, runeAddress)?.toPendingFetch(pool)
        } catch (e: IOException) {
            Timber.w(e, "Failed to read pending liquidity for pool %s", pool)
            null
        } catch (e: NetworkException) {
            Timber.w(e, "Failed to read pending liquidity for pool %s", pool)
            null
        }

    /**
     * Resolves how long each half-deposit has before it is refunded. A failed read leaves the
     * countdown null rather than inventing a deadline the user might plan around.
     */
    private suspend fun withRefundCountdown(
        pending: List<PendingFetch>
    ): List<ThorChainPendingLpDeposit> {
        val (ageLimit, currentHeight) =
            coroutineScope {
                val ageLimitTask = async { fetchPendingLiquidityAgeLimit() }
                val heightTask = async { fetchLastBlockOrNull() }
                ageLimitTask.await() to heightTask.await()
            }

        return pending.map { (deposit, lastAddHeight) ->
            if (ageLimit == null || currentHeight == null || lastAddHeight == null) deposit
            else
                deposit.copy(
                    blocksUntilRefund = (lastAddHeight + ageLimit - currentHeight).coerceAtLeast(0L)
                )
        }
    }

    private suspend fun fetchPendingLiquidityAgeLimit(): Long? =
        try {
            thorChainApi.getMimir()[PENDING_LIQUIDITY_AGE_LIMIT_MIMIR]?.takeIf { it > 0 }
                ?: DEFAULT_PENDING_LIQUIDITY_AGE_LIMIT
        } catch (e: IOException) {
            Timber.w(e, "Failed to read PendingLiquidityAgeLimit")
            null
        } catch (e: NetworkException) {
            Timber.w(e, "Failed to read PendingLiquidityAgeLimit")
            null
        }

    private suspend fun fetchLastBlockOrNull(): Long? =
        try {
            thorChainApi.getLastBlock().takeIf { it > 0 }
        } catch (e: IOException) {
            Timber.w(e, "Failed to read THORChain height")
            null
        } catch (e: NetworkException) {
            Timber.w(e, "Failed to read THORChain height")
            null
        }

    /**
     * Reads an LP record as a half-finished symmetric add, or `null` when nothing is pending on it.
     * Both sides are checked: the user may have sent either half first.
     *
     * A nonzero `units` does not rule a record out. Thornode stages a top-up's pending halves onto
     * the same record that already holds a live position, so skipping those would hide exactly the
     * half-deposit a returning LP needs to complete before it is refunded.
     */
    private fun ThorChainLiquidityProviderJson.toPendingFetch(pool: String): PendingFetch? {
        val runePending = pendingRune.toBigIntegerOrNull() ?: BigInteger.ZERO
        val assetPending = pendingAsset.toBigIntegerOrNull() ?: BigInteger.ZERO
        if (runePending.signum() <= 0 && assetPending.signum() <= 0) return null

        return PendingFetch(
            deposit =
                ThorChainPendingLpDeposit(
                    pool = pool,
                    pendingRune = runePending,
                    pendingAsset = assetPending,
                    pendingTxId = pendingTxId?.takeIf { it.isNotBlank() },
                    pairedAddress =
                        if (runePending.signum() > 0) assetAddress?.takeIf { it.isNotBlank() }
                        else runeAddress?.takeIf { it.isNotBlank() },
                    blocksUntilRefund = null,
                ),
            lastAddHeight = lastAddHeight?.takeIf { it > 0 },
        )
    }

    /**
     * A pending deposit plus the block it was recorded at. The height only exists to derive the
     * refund countdown once, after the scan, so it stays out of the domain model.
     */
    private data class PendingFetch(
        val deposit: ThorChainPendingLpDeposit,
        val lastAddHeight: Long?,
    )
}
