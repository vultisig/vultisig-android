package com.vultisig.wallet.data.blockchain.solana.kamino

import java.math.BigInteger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import wallet.core.jni.Base58

/**
 * Which side of a vault a transaction is on. Selects the compute-unit budget and the validator's
 * rules.
 */
enum class KaminoAction {
    DEPOSIT,
    WITHDRAW,
}

/**
 * The ComputeBudget a transaction carries: the unit limit it reserves and the micro-lamport price
 * it pays per unit.
 *
 * The two together are the priority fee — one without the other prices nothing — so they are
 * compared as a pair. A co-signing device holds a second, independent claim about them in
 * `BlockChainSpecific.Solana`, and the two have to agree before either is put on a screen.
 */
data class KaminoPriorityFee(val limit: BigInteger, val price: BigInteger)

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

    /** The ComputeBudget program both injected instructions invoke. */
    const val PROGRAM_ID = "ComputeBudget111111111111111111111111111111"

    /**
     * Stands for a ComputeBudget that is present but unreadable — a duplicate, a truncated
     * argument, an instruction that is neither of the two. Negative on purpose: it can equal no
     * budget a payload could legitimately record, so a comparison against it always disagrees.
     */
    val MALFORMED =
        KaminoPriorityFee(limit = BigInteger.ONE.negate(), price = BigInteger.ONE.negate())

    /** `ComputeBudgetInstruction::SetComputeUnitLimit`, borsh discriminator 2, then a `u32`. */
    const val SET_UNIT_LIMIT_DISCRIMINATOR = 2

    /** `ComputeBudgetInstruction::SetComputeUnitPrice`, borsh discriminator 3, then a `u64`. */
    const val SET_UNIT_PRICE_DISCRIMINATOR = 3

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

    /**
     * One limit for both withdraw shapes, sized for the expensive one.
     *
     * A withdraw of **farm-staked** shares runs two extra `farms` instructions and a second account
     * creation ahead of the vault withdraw, costing about two-thirds more than the unstaked path:
     * 283,786 (USDC, partial), 289,486 (USDC, at the maximum) and 309,310 (wrapped SOL, which also
     * closes the payout account), against 173,385 unstaked. At 220,000 every staked shape aborted
     * in simulation with `ProgramFailedToComplete — exceeded CUs meter`, so a staked withdraw —
     * which is what essentially every real holder needs — could not be prepared at all.
     *
     * Deliberately not split per shape even though the caller knows which it is building. The
     * priority fee is price × limit, so the unstaked path pays for headroom it does not use (about
     * a third of a cent at the fallback price) and in exchange one value per operation can be
     * pinned. A second value would have to be selected from the transaction's own shape, which is
     * the sort of check that agrees with whatever it is shown.
     */
    private const val WITHDRAW_UNIT_LIMIT = 400_000L

    /**
     * Floor for the micro-lamports-per-unit price when the network's recent-fee sample is
     * unavailable. The fee is price × limit, so at 320,000 units this is 6,400 lamports.
     */
    val FALLBACK_UNIT_PRICE: BigInteger = BigInteger.valueOf(20_000)

    /**
     * Ceiling on the sampled price, in micro-lamports per compute unit.
     *
     * The sample is a number a remote node hands us, multiplied by a six-figure limit to produce
     * lamports the user pays, so unbounded it is an unbounded fee. At this ceiling and the largest
     * limit the priority fee tops out at 400,000 lamports (0.0004 SOL).
     *
     * It is also a cross-platform contract, not just a spend cap: iOS clamps into the same `[floor,
     * ceiling]` range and its decoder *refuses* a transaction priced outside it
     * (`KaminoTransactionDecoder.swift`), so anything above this is a transaction an iPhone
     * co-signer will not join. That is not a hypothetical here — the sample this receives comes
     * from `SolanaApi.getMedianPriorityFee`, which floors at the app-wide 1,000,000 and caps at
     * 100,000,000, so every congested-network sample would land above the ceiling unclamped.
     */
    val MAX_UNIT_PRICE: BigInteger = BigInteger.valueOf(1_000_000)

    /**
     * The compute budget carried by [instructions], or null when they carry none.
     *
     * Reads what the bytes say rather than what anything alongside them claims: this is the figure
     * the runtime will charge against, and on a co-signing device it is the only side of the fee
     * row that is not a display field somebody else filled in.
     *
     * Malformed is not the same as absent, so a ComputeBudget instruction this cannot parse — or a
     * second one of either kind — answers [MALFORMED] rather than null. A caller comparing against
     * a payload must refuse it: "cannot be read" agreeing with "there is none" is how a stripped
     * budget would pass for a matching one.
     */
    fun readFrom(instructions: List<KaminoTxInstruction>): KaminoPriorityFee? {
        val budget = instructions.filter { it.programId == PROGRAM_ID }
        if (budget.isEmpty()) return null

        val limits = budget.mapNotNull { unitLimitArgument(it.data) }
        val prices = budget.mapNotNull { unitPriceArgument(it.data) }
        if (budget.size != 2 || limits.size != 1 || prices.size != 1) return MALFORMED

        return KaminoPriorityFee(limit = limits.single(), price = prices.single())
    }

    /** `SetComputeUnitLimit`'s `u32` argument, or null when [data] is not that instruction. */
    fun unitLimitArgument(data: ByteArray): BigInteger? =
        littleEndianArgument(data, SET_UNIT_LIMIT_DISCRIMINATOR, UNSIGNED_INT_BYTES)

    /** `SetComputeUnitPrice`'s `u64` argument, or null when [data] is not that instruction. */
    fun unitPriceArgument(data: ByteArray): BigInteger? =
        littleEndianArgument(data, SET_UNIT_PRICE_DISCRIMINATOR, UNSIGNED_LONG_BYTES)

    /**
     * The little-endian argument of a ComputeBudget instruction, when [data] is exactly the
     * discriminator followed by [width] bytes. A longer instruction is refused rather than read
     * from its prefix: trailing bytes mean this is not the instruction it appears to be.
     */
    private fun littleEndianArgument(data: ByteArray, discriminator: Int, width: Int): BigInteger? {
        if (data.size != width + 1) return null
        if ((data[0].toInt() and 0xFF) != discriminator) return null
        var value = BigInteger.ZERO
        for (offset in width - 1 downTo 0) {
            value = value.shiftLeft(8).or(BigInteger.valueOf(data[1 + offset].toLong() and 0xFF))
        }
        return value
    }

    private const val UNSIGNED_INT_BYTES = 4

    private const val UNSIGNED_LONG_BYTES = 8

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

    /**
     * Brings a sampled network price inside `[FALLBACK_UNIT_PRICE, MAX_UNIT_PRICE]`, the same range
     * iOS clamps into and the only range its decoder accepts.
     *
     * The floor is a floor rather than a default: a sample below it would under-tip a transaction
     * that has to land before its blockhash expires.
     */
    fun unitPriceFor(networkPrice: BigInteger?): BigInteger =
        (networkPrice ?: FALLBACK_UNIT_PRICE).coerceIn(FALLBACK_UNIT_PRICE, MAX_UNIT_PRICE)

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

    /**
     * The `SetComputeUnitPrice` instruction as WalletCore's JSON instruction format, ready to
     * insert at a chosen position.
     *
     * Built here rather than left to `SolanaTransaction.setComputeUnitPrice`, which appends: the
     * two budget instructions have to sit together at the front, and the helper would leave the
     * price behind every instruction Kamino built. See [KaminoTransactionPreparer] for why the
     * position is part of the cross-platform contract.
     *
     * The instruction takes no accounts, and `data` is base58 — the encoding WalletCore's JSON
     * format expects, not base64.
     */
    fun setUnitPriceInstructionJson(price: BigInteger): String =
        buildJsonObject {
                put("programId", PROGRAM_ID)
                put("accounts", JsonArray(emptyList()))
                put("data", Base58.encodeNoCheck(setUnitPriceData(price)))
            }
            .toString()

    /**
     * Borsh-encoded `SetComputeUnitPrice`: the discriminator byte followed by the micro-lamport
     * price as a little-endian `u64`.
     */
    fun setUnitPriceData(price: BigInteger): ByteArray {
        // The sign is checked separately: `bitLength` ignores it, so -1 would clear the bound.
        require(price.signum() >= 0 && price.bitLength() <= Long.SIZE_BITS) {
            "compute-unit price $price does not fit a u64"
        }
        return byteArrayOf(SET_UNIT_PRICE_DISCRIMINATOR.toByte()) +
            ByteArray(Long.SIZE_BYTES) { byte -> price.shiftRight(8 * byte).toInt().toByte() }
    }
}
