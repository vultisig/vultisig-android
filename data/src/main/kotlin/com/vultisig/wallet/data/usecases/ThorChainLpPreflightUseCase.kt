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

    /**
     * The pool asset's chain refuses the add: mimir `HALT<CHAIN>LP`/`HALT<CHAIN>CHAIN`, or — for a
     * RUNE-side add — its `/thorchain/inbound_addresses` entry reporting a chain halt, a trading
     * halt, or paused LP actions.
     */
    data class ChainLpHalted(val chainPrefix: String) : ThorChainLpPreflightBlock()

    /** Pool exists but its `status` takes no adds at all (typically `Suspended`). */
    data class PoolNotAvailable(val pool: String, val status: String?) :
        ThorChainLpPreflightBlock()

    /**
     * Pool is `Staged`, which THORChain opens only to dual-sided adds: an add that does not name the
     * other side's address is refunded.
     */
    data class StagedPoolRequiresPairedAdd(val pool: String) : ThorChainLpPreflightBlock()
}

/**
 * Validates THORChain network state before an add-liquidity inbound is signed and broadcast. By
 * reading mimir, the pool's `status` and, for a RUNE-side add, the pool asset chain's inbound
 * flags, we refuse to build the keysign payload when the network would refuse it.
 *
 * The asset chain is identified by the pool prefix (e.g. `ETH` from `ETH.USDT-0xdac…`). thornode
 * judges a RUNE-side `MsgDeposit` add by that chain's trading and LP halts, not by THORChain's: it
 * fails the add in the block after the ante handler has already taken the native fee. The
 * `/thorchain/inbound_addresses` entry for the chain carries exactly those halts, evaluated at the
 * current height by the same keeper functions the handlers call, so it is read instead of more raw
 * mimir keys whose activation heights a plain `> 0` read would get wrong.
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
     * @param isPairedAdd whether the memo names the other side's address, making this one leg of a
     *   dual-sided add — the only kind a `Staged` pool accepts.
     * @param checkAssetChainInbound whether to also block on the pool asset chain's inbound halt
     *   flags. True for a RUNE-side add; the asset-side add has its own inbound gate.
     */
    suspend operator fun invoke(
        pool: String,
        isPairedAdd: Boolean,
        checkAssetChainInbound: Boolean,
    ): ThorChainLpPreflightBlock?
}

internal class ThorChainLpPreflightUseCaseImpl
@Inject
constructor(
    private val thorChainApi: ThorChainApi,
    private val mimirRepository: ThorMimirRepository,
) : ThorChainLpPreflightUseCase {

    override suspend fun invoke(
        pool: String,
        isPairedAdd: Boolean,
        checkAssetChainInbound: Boolean,
    ): ThorChainLpPreflightBlock? = coroutineScope {
        val chainPrefix = pool.substringBefore('.', missingDelimiterValue = "").uppercase()

        val lpPausedDeferred = async { probe { mimirRepository.isLpPaused(pool) } }
        val chainHaltedDeferred = async {
            if (chainPrefix.isEmpty()) null else probe { mimirRepository.isLpHalted(chainPrefix) }
        }
        val inboundHaltedDeferred = async {
            if (!checkAssetChainInbound || chainPrefix.isEmpty() || chainPrefix == THOR_PREFIX) null
            else probe { isInboundHalted(chainPrefix) }
        }
        val poolDeferred = async { probe { thorChainApi.getPool(pool) } }

        if (lpPausedDeferred.await() == true) {
            cancelAll(chainHaltedDeferred, inboundHaltedDeferred, poolDeferred)
            return@coroutineScope ThorChainLpPreflightBlock.LpPaused(pool)
        }
        if (chainHaltedDeferred.await() == true || inboundHaltedDeferred.await() == true) {
            cancelAll(inboundHaltedDeferred, poolDeferred)
            return@coroutineScope ThorChainLpPreflightBlock.ChainLpHalted(chainPrefix)
        }

        val status = poolDeferred.await()?.status
        when {
            status == null || status.equals(POOL_STATUS_AVAILABLE, ignoreCase = true) -> null
            status.equals(POOL_STATUS_STAGED, ignoreCase = true) ->
                if (isPairedAdd) null
                else ThorChainLpPreflightBlock.StagedPoolRequiresPairedAdd(pool)
            else -> ThorChainLpPreflightBlock.PoolNotAvailable(pool, status)
        }
    }

    private suspend fun isInboundHalted(chainPrefix: String): Boolean? =
        thorChainApi
            .getTHORChainInboundAddresses()
            .firstOrNull { it.chain.equals(chainPrefix, ignoreCase = true) }
            ?.let {
                it.halted ||
                    it.globalTradingPaused ||
                    it.chainTradingPaused ||
                    it.chainLPActionsPaused
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
        const val POOL_STATUS_STAGED = "Staged"
        const val THOR_PREFIX = "THOR"
    }
}
