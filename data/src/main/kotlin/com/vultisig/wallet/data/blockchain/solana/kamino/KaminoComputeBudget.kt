package com.vultisig.wallet.data.blockchain.solana.kamino

import java.math.BigInteger

/**
 * Which side of a vault a transaction is on. Selects the compute-unit budget and the validator's
 * rules.
 */
enum class KaminoAction {
    DEPOSIT,
    WITHDRAW,
}

/**
 * Compute-unit budget for Kamino transactions.
 *
 * Kamino builds its transactions with no ComputeBudget instruction at all and ignores priority-fee
 * fields in the request body — the response is byte-identical with or without them. So the only way
 * one of these transactions carries a priority fee is for the app to inject it, and injecting a
 * price without a limit would price the fee off the runtime's default limit rather than the units
 * the transaction actually needs.
 */
object KaminoComputeBudget {

    /**
     * Compute units these transactions actually consume, measured on mainnet via
     * `simulateTransaction`: a USDC deposit 252,146, a SOL deposit 287,029, a withdraw 174,566.
     * Each limit below carries roughly a quarter more, absorbing the drift a different
     * account-existence mix causes — a deposit that still has to create its share account or farm
     * user account does strictly more work than one whose accounts already exist.
     *
     * Deliberately **not** `SOLANA_PRIORITY_FEE_LIMIT`. That constant is 100,000 units, below what
     * every one of these transactions consumes, so reusing it would abort each of them on compute
     * exhaustion.
     */
    private const val TOKEN_DEPOSIT_UNIT_LIMIT = 320_000L

    /** A SOL-vault deposit also creates the wSOL account, transfers into it and syncs it. */
    private const val NATIVE_DEPOSIT_UNIT_LIMIT = 350_000L

    private const val WITHDRAW_UNIT_LIMIT = 220_000L

    /**
     * Floor for the micro-lamports-per-unit price when the network's recent-fee sample is
     * unavailable. The fee is price × limit, so at 320,000 units this is 6,400 lamports.
     */
    val FALLBACK_UNIT_PRICE: BigInteger = BigInteger.valueOf(20_000)

    fun unitLimitFor(vault: KaminoVault, action: KaminoAction): BigInteger =
        when (action) {
            KaminoAction.WITHDRAW -> WITHDRAW_UNIT_LIMIT
            KaminoAction.DEPOSIT ->
                if (vault.tokenMint == KaminoVaultRegistry.WRAPPED_SOL_MINT) {
                    NATIVE_DEPOSIT_UNIT_LIMIT
                } else {
                    TOKEN_DEPOSIT_UNIT_LIMIT
                }
        }.let(BigInteger::valueOf)

    /** Never price below the floor, however stale or absent the network sample is. */
    fun unitPriceFor(networkPrice: BigInteger?): BigInteger =
        networkPrice?.takeIf { it > FALLBACK_UNIT_PRICE } ?: FALLBACK_UNIT_PRICE

    /**
     * The priority fee this transaction will actually be charged, in lamports.
     *
     * price × limit, converted from micro-lamports. A Max-amount form has to subtract this or the
     * transaction it builds cannot pay for itself. Rounded up, so the headroom is never a lamport
     * short of what the runtime charges.
     */
    fun priorityFeeLamports(
        vault: KaminoVault,
        action: KaminoAction,
        networkPrice: BigInteger?,
    ): BigInteger {
        val microLamports = unitPriceFor(networkPrice).multiply(unitLimitFor(vault, action))
        val (lamports, remainder) = microLamports.divideAndRemainder(MICRO_LAMPORTS_PER_LAMPORT)
        return if (remainder.signum() > 0) lamports.add(BigInteger.ONE) else lamports
    }

    private val MICRO_LAMPORTS_PER_LAMPORT = BigInteger.valueOf(1_000_000)
}
