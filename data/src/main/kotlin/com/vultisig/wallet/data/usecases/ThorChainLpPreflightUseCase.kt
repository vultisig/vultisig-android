package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.repositories.ThorMimirRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Reason a THORChain add-liquidity inbound was blocked before it could be broadcast.
 *
 * Each case mirrors a signal the network publishes that — left unchecked — would have caused
 * THORChain to accept the inbound, refund it, and leave the user out the inbound gas.
 */
sealed class ThorChainLpPreflightBlock {
    /** The pool is paused via `PAUSELP` (global) or `PAUSELP-<CHAIN>-<TICKER>` (per-asset). */
    data class LpPaused(val pool: String) : ThorChainLpPreflightBlock()

    /** LP activity on the asset's chain is halted via `HALT<CHAIN>LP`/`HALT<CHAIN>CHAIN`. */
    data class ChainLpHalted(val chainPrefix: String) : ThorChainLpPreflightBlock()

    /**
     * Inbound addresses for the asset's chain report `halted`, `chain_lp_actions_paused`, or
     * `global_trading_paused`. [chainPrefix] is the THORChain inbound-chain identifier (BTC/ETH/…).
     */
    data class InboundLpPaused(val chainPrefix: String) : ThorChainLpPreflightBlock()

    /** Pool exists but its `status` is not `Available` (typically `Staged` or `Suspended`). */
    data class PoolNotAvailable(val pool: String, val status: String?) :
        ThorChainLpPreflightBlock()
}

/**
 * Validates THORChain network state before an LP add-liquidity inbound is signed and broadcast.
 *
 * Companion to #4468 (refund *reporting*) — this use case is the *prevention* half: by reading
 * mimir, the pool's `status`, and the inbound `chain_lp_actions_paused` for the asset chain, we
 * refuse to build the keysign payload when the network would refund it.
 *
 * The asset chain is the chain identified by the pool prefix (e.g. `ETH` from `ETH.USDT-0xdac…`).
 * The native chain (THORChain) itself is never the inbound-chain target for an LP add — RUNE
 * deposits go on-chain locally — so we only check the asset chain's inbound flags.
 */
interface ThorChainLpPreflightUseCase {
    /**
     * Returns the first blocking reason found, or `null` when the LP add is safe to broadcast.
     * Calls are parallelized; failures of individual signals are swallowed (fail-open) so a
     * transient thornode hiccup does not block a healthy deposit.
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

        val lpPausedDeferred = async {
            runCatching { mimirRepository.isLpPaused(pool) }.getOrNull()
        }
        val chainHaltedDeferred = async {
            if (chainPrefix.isEmpty()) null
            else runCatching { mimirRepository.isLpHalted(chainPrefix) }.getOrNull()
        }
        val poolDeferred = async { runCatching { thorChainApi.getPool(pool) }.getOrNull() }
        val inboundDeferred = async {
            runCatching { thorChainApi.getTHORChainInboundAddresses() }.getOrNull()
        }

        if (lpPausedDeferred.await() == true) {
            return@coroutineScope ThorChainLpPreflightBlock.LpPaused(pool)
        }
        if (chainHaltedDeferred.await() == true) {
            return@coroutineScope ThorChainLpPreflightBlock.ChainLpHalted(chainPrefix)
        }

        val poolJson = poolDeferred.await()
        if (poolJson != null) {
            val status = poolJson.status
            if (status != null && !status.equals(POOL_STATUS_AVAILABLE, ignoreCase = true)) {
                return@coroutineScope ThorChainLpPreflightBlock.PoolNotAvailable(pool, status)
            }
        }

        val inbound =
            inboundDeferred.await()?.firstOrNull { it.chain.equals(chainPrefix, ignoreCase = true) }
        if (inbound != null) {
            val blocked =
                inbound.halted || inbound.chainLPActionsPaused || inbound.globalTradingPaused
            if (blocked) {
                return@coroutineScope ThorChainLpPreflightBlock.InboundLpPaused(chainPrefix)
            }
        }

        null
    }

    private companion object {
        const val POOL_STATUS_AVAILABLE = "Available"
    }
}
