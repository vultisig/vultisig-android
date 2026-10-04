package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.MayaNodePool
import com.vultisig.wallet.data.api.models.MayaLiquidityProviderJson
import com.vultisig.wallet.data.models.MayaChainPendingLpDeposit
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/** Mimir override for the blocks MayaChain holds a half-deposit before refunding it. */
private const val PENDING_LIQUIDITY_AGE_LIMIT_MIMIR = "PENDINGLIQUIDITYAGELIMIT"

/**
 * mayanode's `PendingLiquidityAgeLimit` constant, used when mimir carries no override: 100,800
 * blocks, roughly a week at MayaChain's ~6 s block time.
 */
private const val DEFAULT_PENDING_LIQUIDITY_AGE_LIMIT = 100_800L

interface GetMayaChainPendingLpDepositsUseCase {
    /**
     * Finds the vault's half-finished symmetric MayaChain adds.
     *
     * The scan starts from `/mayachain/pools`, whose `pending_inbound_*` fields say which pools hold
     * anyone's pending liquidity; only those are then read for [cacaoAddress], the address mayanode
     * keys every paired add by.
     */
    suspend operator fun invoke(cacaoAddress: String): List<MayaChainPendingLpDeposit>
}

internal class GetMayaChainPendingLpDepositsUseCaseImpl
@Inject
constructor(private val mayaChainApi: MayaChainApi) : GetMayaChainPendingLpDepositsUseCase {

    override suspend fun invoke(cacaoAddress: String): List<MayaChainPendingLpDeposit> {
        val candidates =
            probe("scan pools for pending liquidity") {
                mayaChainApi.getMayaNodePools().filter { it.holdsPendingLiquidity() }
            } ?: return emptyList()
        if (candidates.isEmpty()) return emptyList()

        val found = coroutineScope {
            candidates
                .map { pool ->
                    async {
                        probe("read pending liquidity for ${pool.asset}") {
                            mayaChainApi
                                .getLiquidityProvider(pool.asset, cacaoAddress)
                                ?.toPendingFetch(pool.asset)
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
        if (found.isEmpty()) return emptyList()

        return withRefundCountdown(found)
    }

    private fun MayaNodePool.holdsPendingLiquidity(): Boolean =
        (pendingInboundCacao.toBigIntegerOrNull()?.signum() ?: 0) > 0 ||
            (pendingInboundAsset.toBigIntegerOrNull()?.signum() ?: 0) > 0

    private suspend fun withRefundCountdown(
        pending: List<PendingFetch>
    ): List<MayaChainPendingLpDeposit> {
        val (ageLimit, currentHeight) =
            coroutineScope {
                val ageLimitTask = async {
                    probe("read PendingLiquidityAgeLimit") {
                        mayaChainApi.getMayaConstants()[PENDING_LIQUIDITY_AGE_LIMIT_MIMIR]
                            ?.takeIf { it > 0 } ?: DEFAULT_PENDING_LIQUIDITY_AGE_LIMIT
                    }
                }
                val heightTask = async {
                    probe("read MayaChain height") {
                        mayaChainApi.getLatestBlock().block.header.height.toLong().takeIf {
                            it > 0
                        }
                    }
                }
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

    /**
     * Reads a record as a half-finished paired add, or `null` when nothing is pending on it. A
     * nonzero `units` does not rule it out: a top-up's pending half sits on a live position's record.
     */
    private fun MayaLiquidityProviderJson.toPendingFetch(pool: String): PendingFetch? {
        val cacaoPending = pendingCacao.toBigIntegerOrNull() ?: BigInteger.ZERO
        val assetPending = pendingAsset.toBigIntegerOrNull() ?: BigInteger.ZERO
        if (cacaoPending.signum() <= 0 && assetPending.signum() <= 0) return null

        return PendingFetch(
            deposit =
                MayaChainPendingLpDeposit(
                    pool = pool,
                    pendingCacao = cacaoPending,
                    pendingAsset = assetPending,
                    pendingTxId = pendingTxId?.takeIf { it.isNotBlank() },
                    pairedAddress =
                        if (cacaoPending.signum() > 0) assetAddress?.takeIf { it.isNotBlank() }
                        else cacaoAddress?.takeIf { it.isNotBlank() },
                    blocksUntilRefund = null,
                ),
            lastAddHeight = lastAddHeight?.takeIf { it > 0 },
        )
    }

    /** A failed read is logged and dropped; cancellation still propagates. */
    private suspend fun <T> probe(what: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to %s", what)
            null
        }

    private data class PendingFetch(
        val deposit: MayaChainPendingLpDeposit,
        val lastAddHeight: Long?,
    )
}
