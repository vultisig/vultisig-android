package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Reason a MayaChain add-liquidity inbound was blocked before it could be broadcast.
 *
 * Each case mirrors a check mayanode's add-liquidity handler runs — left unchecked, MayaChain would
 * accept the inbound, refund it, and leave the user out the inbound gas.
 */
sealed class MayaChainLpPreflightBlock {
    /** Add-liquidity is paused via mimir `PAUSELP` (global) or `PAUSELP<CHAIN>`. */
    data class LpPaused(val pool: String) : MayaChainLpPreflightBlock()

    /** The pool's asset chain is halted, or its inbound reports LP actions paused. */
    data class ChainHalted(val chainPrefix: String) : MayaChainLpPreflightBlock()

    /** Pool is missing or its `status` takes no adds at all (typically `Suspended`). */
    data class PoolNotAvailable(val pool: String, val status: String?) :
        MayaChainLpPreflightBlock()

    /** Pool is `Staged`, which MayaChain opens only to adds naming the other side's address. */
    data class StagedPoolRequiresPairedAdd(val pool: String) : MayaChainLpPreflightBlock()
}

/**
 * Validates MayaChain network state before either side of an add-liquidity inbound is signed.
 *
 * mayanode's `IsLPPaused` reads `PauseLP` and `PauseLP<CHAIN>` as activation heights — a key pauses
 * adds only once the chain is past it — so the latest block height is read alongside mimir.
 */
interface MayaChainLpPreflightUseCase {
    /**
     * Returns the first blocking reason found, or `null` when the add is safe to broadcast. Signals
     * are fetched in parallel and each fails open, so a transient mayanode hiccup does not block a
     * healthy deposit. `CancellationException` is propagated.
     *
     * @param pool MayaChain pool id, e.g. `BTC.BTC` or `ARB.ETH`.
     * @param isPairedAdd whether the memo names the other side's address — the only kind of add a
     *   `Staged` pool accepts.
     */
    suspend operator fun invoke(pool: String, isPairedAdd: Boolean): MayaChainLpPreflightBlock?
}

internal class MayaChainLpPreflightUseCaseImpl
@Inject
constructor(private val mayaChainApi: MayaChainApi) : MayaChainLpPreflightUseCase {

    override suspend fun invoke(pool: String, isPairedAdd: Boolean): MayaChainLpPreflightBlock? =
        coroutineScope {
            val chainPrefix = pool.substringBefore('.', missingDelimiterValue = "").uppercase()

            val mimirDeferred = async { probe { mayaChainApi.getMayaConstants() } }
            val heightDeferred = async {
                probe { mayaChainApi.getLatestBlock().block.header.height.toLong() }
            }
            val inboundDeferred = async {
                if (chainPrefix.isEmpty() || chainPrefix == MAYA_CHAIN_PREFIX) null
                else
                    probe {
                        mayaChainApi.getInboundAddresses().firstOrNull {
                            it.chain.equals(chainPrefix, ignoreCase = true)
                        }
                    }
            }
            val poolDeferred = async { probe { mayaChainApi.getPool(pool) } }

            val mimir = mimirDeferred.await()
            val height = heightDeferred.await()
            if (
                mimir != null &&
                    (isActive(mimir[KEY_PAUSE_LP], height) ||
                        isActive(mimir[KEY_PAUSE_LP + chainPrefix], height))
            ) {
                return@coroutineScope MayaChainLpPreflightBlock.LpPaused(pool)
            }

            val inbound = inboundDeferred.await()
            if (inbound != null && (inbound.halted || inbound.chainLPActionsPaused)) {
                return@coroutineScope MayaChainLpPreflightBlock.ChainHalted(chainPrefix)
            }

            val status = poolDeferred.await()?.status
            when {
                status == null || status.equals(POOL_STATUS_AVAILABLE, ignoreCase = true) -> null
                status.equals(POOL_STATUS_STAGED, ignoreCase = true) ->
                    if (isPairedAdd) null
                    else MayaChainLpPreflightBlock.StagedPoolRequiresPairedAdd(pool)
                else -> MayaChainLpPreflightBlock.PoolNotAvailable(pool, status)
            }
        }

    /**
     * Mirrors mayanode's `value > 0 && value < height`. An unknown height counts any set key as
     * active, so a failed height read cannot let a configured pause through.
     */
    private fun isActive(value: Long?, height: Long?): Boolean =
        value != null && value > 0 && (height == null || value < height)

    private suspend fun <T> probe(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val KEY_PAUSE_LP = "PAUSELP"
        const val MAYA_CHAIN_PREFIX = "MAYA"
        const val POOL_STATUS_AVAILABLE = "Available"
        const val POOL_STATUS_STAGED = "Staged"
    }
}
