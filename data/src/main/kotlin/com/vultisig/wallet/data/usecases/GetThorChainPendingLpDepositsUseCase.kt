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

/** `PendingLiquidityAgeLimit`'s constant value, used when mimir carries no override (~1 day). */
private const val DEFAULT_PENDING_LIQUIDITY_AGE_LIMIT = 17_280L

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
     * addresses, so the usual cost is one request rather than one per pool.
     *
     * @param runeAddress the user's RUNE address — one side of every symmetric add.
     * @param resolveAssetAddress the user's address on a pool's non-RUNE chain, or `null` when the
     *   vault has no account there. Called only for pools that hold pending liquidity.
     */
    suspend operator fun invoke(
        runeAddress: String,
        resolveAssetAddress: suspend (poolId: String) -> String?,
    ): List<ThorChainPendingLpDeposit>
}

internal class GetThorChainPendingLpDepositsUseCaseImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : GetThorChainPendingLpDepositsUseCase {

    override suspend fun invoke(
        runeAddress: String,
        resolveAssetAddress: suspend (poolId: String) -> String?,
    ): List<ThorChainPendingLpDeposit> {
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
                .map { pool ->
                    async { fetchPending(pool.asset, runeAddress, resolveAssetAddress(pool.asset)) }
                }
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
     * Reads the user's LP record on [pool] from whichever side carries it. THORChain keys a
     * symmetric add by the address that opened it, which may be either side, and the unused side
     * can still answer with an empty husk — so a record without pending amounts does not end the
     * search.
     */
    private suspend fun fetchPending(
        pool: String,
        runeAddress: String,
        assetAddress: String?,
    ): PendingFetch? =
        try {
            val runeSide = thorChainApi.getLiquidityProvider(pool, runeAddress)
            val record =
                runeSide?.takeIf { it.hasPending() }
                    ?: assetAddress
                        ?.takeIf { it.isNotBlank() }
                        ?.let { thorChainApi.getLiquidityProvider(pool, it) }
            record?.toPendingFetch(pool)
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

    private fun ThorChainLiquidityProviderJson.hasPending(): Boolean =
        (pendingRune.toBigIntegerOrNull()?.signum() ?: 0) > 0 ||
            (pendingAsset.toBigIntegerOrNull()?.signum() ?: 0) > 0

    /**
     * Reads an LP record as a half-finished symmetric add, or `null` when nothing is pending on it.
     * Both sides are checked: the user may have sent either half first. A record that already holds
     * units is a live position, not a pending deposit, even if a later add is still settling.
     */
    private fun ThorChainLiquidityProviderJson.toPendingFetch(pool: String): PendingFetch? {
        if ((units.toBigIntegerOrNull()?.signum() ?: 0) > 0) return null

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
