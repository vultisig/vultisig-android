package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.repositories.ThorMimirRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Reason a THORChain add-liquidity inbound was blocked before it could be broadcast.
 *
 * Each case mirrors a signal the network publishes that — left unchecked — would have caused
 * THORChain to accept the inbound, refund it, and leave the user out the inbound gas.
 */
sealed class ThorChainLpPreflightBlock {
    /**
     * Add-liquidity is paused via `PAUSELP` (global) or `PAUSELPDEPOSIT-<CHAIN>-<ASSET[-CONTRACT]>`
     * (per-pool).
     */
    data class LpPaused(val pool: String) : ThorChainLpPreflightBlock()

    /** LP activity on the asset's chain is halted via `HALT<CHAIN>LP`/`HALT<CHAIN>CHAIN`. */
    data class ChainLpHalted(val chainPrefix: String) : ThorChainLpPreflightBlock()

    /** Pool exists but its `status` is not `Available` (typically `Staged` or `Suspended`). */
    data class PoolNotAvailable(val pool: String, val status: String?) :
        ThorChainLpPreflightBlock()
}

/**
 * Validates THORChain network state before a RUNE-side add-liquidity inbound is signed and
 * broadcast. By reading mimir and the pool's `status`, we refuse to build the keysign payload when
 * the network would refund it.
 *
 * Scope is intentionally limited to the RUNE side: a RUNE deposit never traverses the asset-chain
 * inbound, so asset-chain inbound flags are not inspected. The asset chain is identified by the
 * pool prefix (e.g. `ETH` from `ETH.USDT-0xdac…`) and is only used for the mimir
 * `HALT<CHAIN>LP`/`HALT<CHAIN>CHAIN` checks, which capture LP-specific halts independently of
 * inbound trading state.
 */
interface ThorChainLpPreflightUseCase {
    /**
     * Returns the first blocking reason found, or `null` when the LP add is safe to broadcast.
     * Calls are parallelized; failures of individual signals are swallowed (fail-open) so a
     * transient thornode hiccup does not block a healthy deposit. `CancellationException` is
     * propagated so parent-scope cancellation still works.
     *
     * @param pool canonical THORChain pool id, e.g.
     *   `ETH.USDT-0xdac17f958d2ee523a2206206994597c13d831ec7`.
     */
    suspend operator fun invoke(pool: String): ThorChainLpPreflightBlock?
}

internal class ThorChainLpPreflightUseCaseImpl
@Inject
constructor(
    private val thorChainApi: ThorChainApi,
    private val mimirRepository: ThorMimirRepository,
) : ThorChainLpPreflightUseCase {

    override suspend fun invoke(pool: String): ThorChainLpPreflightBlock? = coroutineScope {
        val chainPrefix = pool.substringBefore('.', missingDelimiterValue = "").uppercase()

        val lpPausedDeferred = async { probe { mimirRepository.isLpPaused(pool) } }
        val chainHaltedDeferred = async {
            if (chainPrefix.isEmpty()) null else probe { mimirRepository.isLpHalted(chainPrefix) }
        }
        val poolDeferred = async { probe { thorChainApi.getPool(pool) } }

        if (lpPausedDeferred.await() == true) {
            cancelAll(chainHaltedDeferred, poolDeferred)
            return@coroutineScope ThorChainLpPreflightBlock.LpPaused(pool)
        }
        if (chainHaltedDeferred.await() == true) {
            cancelAll(poolDeferred)
            return@coroutineScope ThorChainLpPreflightBlock.ChainLpHalted(chainPrefix)
        }

        val poolJson = poolDeferred.await()
        if (poolJson != null) {
            val status = poolJson.status
            if (status != null && !status.equals(POOL_STATUS_AVAILABLE, ignoreCase = true)) {
                return@coroutineScope ThorChainLpPreflightBlock.PoolNotAvailable(pool, status)
            }
        }

        null
    }

    private fun cancelAll(vararg deferreds: Deferred<*>) {
        deferreds.forEach { it.cancel() }
    }

    private suspend fun <T> probe(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val POOL_STATUS_AVAILABLE = "Available"
    }
}
