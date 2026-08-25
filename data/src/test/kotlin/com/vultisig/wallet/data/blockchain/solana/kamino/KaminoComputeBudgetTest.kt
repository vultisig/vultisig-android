package com.vultisig.wallet.data.blockchain.solana.kamino

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The limits here have a direct on-chain consequence: too low and the transaction aborts on compute
 * exhaustion, and the user cannot avoid it. The measurements they are sized against are recorded in
 * the source, so these tests pin the values rather than restate the arithmetic.
 */
class KaminoComputeBudgetTest {

    private val usdcVault = KaminoVaultRegistry.STEAKHOUSE_USDC
    private val solVault = KaminoVaultRegistry.ALLEZ_SOL

    /** The app-wide Solana limit, which every one of these transactions exceeds. */
    private val appWideLimit = BigInteger.valueOf(100_000)

    @Test
    fun `every limit clears the app-wide Solana default`() {
        // SOLANA_PRIORITY_FEE_LIMIT is 100,000 units. Reusing it would abort all three shapes.
        listOf(
                usdcVault to KaminoAction.DEPOSIT,
                solVault to KaminoAction.DEPOSIT,
                usdcVault to KaminoAction.WITHDRAW,
                solVault to KaminoAction.WITHDRAW,
            )
            .forEach { (vault, action) ->
                val limit = KaminoComputeBudget.unitLimitFor(vault, action)
                assertTrue(
                    limit > appWideLimit,
                    "${vault.fallbackName} $action got $limit, at or below the 100,000 default",
                )
            }
    }

    @Test
    fun `a token deposit is sized above its measured cost`() {
        // Measured 252,146 on mainnet.
        val limit = KaminoComputeBudget.unitLimitFor(usdcVault, KaminoAction.DEPOSIT)
        assertEquals(BigInteger.valueOf(320_000), limit)
        assertTrue(limit > BigInteger.valueOf(252_146))
    }

    @Test
    fun `a SOL deposit gets more, because it wraps first`() {
        // Measured 287,029: it also creates the wSOL account, transfers into it and syncs it.
        val limit = KaminoComputeBudget.unitLimitFor(solVault, KaminoAction.DEPOSIT)
        assertEquals(BigInteger.valueOf(350_000), limit)
        assertTrue(limit > BigInteger.valueOf(287_029))
        assertTrue(
            limit > KaminoComputeBudget.unitLimitFor(usdcVault, KaminoAction.DEPOSIT),
            "wrapping cannot cost less than not wrapping",
        )
    }

    @Test
    fun `a withdraw is sized for the farm-staked shape, not the unstaked one`() {
        // The staked path runs two extra farms instructions and a second account creation:
        // 283,786 / 289,486 / 309,310 measured, against 173,385 unstaked. At the earlier 220,000
        // every staked shape aborted with ProgramFailedToComplete — and since every deposit
        // auto-stakes, that is the shape essentially every real withdraw takes.
        listOf(usdcVault, solVault).forEach { vault ->
            val limit = KaminoComputeBudget.unitLimitFor(vault, KaminoAction.WITHDRAW)
            assertEquals(BigInteger.valueOf(400_000), limit, vault.fallbackName)
            assertTrue(
                limit > BigInteger.valueOf(309_310),
                "${vault.fallbackName} must clear the most expensive measured staked withdraw",
            )
        }
    }

    @Test
    fun `one withdraw limit covers both shapes rather than one per shape`() {
        // Deliberate: a second value would have to be chosen from the transaction's own shape,
        // which
        // is the sort of check that agrees with whatever it is shown. The unstaked path pays for
        // headroom it does not use instead.
        assertEquals(
            KaminoComputeBudget.unitLimitFor(usdcVault, KaminoAction.WITHDRAW),
            KaminoComputeBudget.unitLimitFor(solVault, KaminoAction.WITHDRAW),
        )
    }

    @Test
    fun `the unit price never falls below the floor`() {
        val floor = KaminoComputeBudget.FALLBACK_UNIT_PRICE
        assertEquals(floor, KaminoComputeBudget.unitPriceFor(null))
        assertEquals(floor, KaminoComputeBudget.unitPriceFor(BigInteger.ONE))
        assertEquals(floor, KaminoComputeBudget.unitPriceFor(floor))

        // A healthier network sample is honoured.
        val higher = floor.add(BigInteger.ONE)
        assertEquals(higher, KaminoComputeBudget.unitPriceFor(higher))
    }

    @Test
    fun `the unit price is capped, because iOS refuses anything above the ceiling`() {
        // Not merely a spend cap. iOS clamps into the same range and its decoder rejects a
        // transaction priced outside it, so an uncapped price is a transaction an iPhone co-signer
        // will not join. And the sample this receives comes from `getMedianPriorityFee`, which
        // already floors at the app-wide 1,000,000 and caps at 100,000,000 — so without this every
        // congested-network sample would sail past the ceiling.
        val ceiling = KaminoComputeBudget.MAX_UNIT_PRICE
        assertEquals(BigInteger.valueOf(1_000_000), ceiling)
        assertEquals(ceiling, KaminoComputeBudget.unitPriceFor(ceiling))
        assertEquals(ceiling, KaminoComputeBudget.unitPriceFor(ceiling.add(BigInteger.ONE)))
        assertEquals(ceiling, KaminoComputeBudget.unitPriceFor(BigInteger.valueOf(100_000_000)))

        // Just below stays exactly where it is: the clamp must not round anything in range.
        val below = ceiling.subtract(BigInteger.ONE)
        assertEquals(below, KaminoComputeBudget.unitPriceFor(below))

        // The ceiling bounds the worst fee too: 1,000,000 x 400,000 / 1e6 = 400,000 lamports.
        assertEquals(
            BigInteger.valueOf(400_000),
            KaminoComputeBudget.priorityFeeLamports(
                usdcVault,
                KaminoAction.WITHDRAW,
                BigInteger.valueOf(100_000_000),
            ),
        )
    }

    @Test
    fun `the priority fee is price times limit, in lamports`() {
        // 20,000 micro-lamports per unit x 320,000 units = 6,400,000,000 micro-lamports = 6,400
        // lamports, which is what bounds the cost of these limits.
        assertEquals(
            BigInteger.valueOf(6_400),
            KaminoComputeBudget.priorityFeeLamports(usdcVault, KaminoAction.DEPOSIT, null),
        )
        // The withdraw limit is the most expensive: 20,000 x 400,000 = 8,000 lamports.
        assertEquals(
            BigInteger.valueOf(8_000),
            KaminoComputeBudget.priorityFeeLamports(usdcVault, KaminoAction.WITHDRAW, null),
        )
    }

    @Test
    fun `a fee with a fractional lamport rounds up, never leaving the headroom short`() {
        // 1 micro-lamport x 320,000 units is 0.32 lamports; a floor would reserve nothing.
        val fee =
            KaminoComputeBudget.priorityFeeLamports(usdcVault, KaminoAction.DEPOSIT, BigInteger.ONE)
        // The price floors to 20,000 first, so this is the ordinary fee — the rounding is exercised
        // by a price that does not divide evenly.
        assertEquals(BigInteger.valueOf(6_400), fee)

        val odd =
            KaminoComputeBudget.priorityFeeLamports(
                usdcVault,
                KaminoAction.DEPOSIT,
                BigInteger.valueOf(20_001),
            )
        // 20,001 x 320,000 = 6,400,320,000 micro-lamports = 6,400.32 lamports, rounded up.
        assertEquals(BigInteger.valueOf(6_401), odd)
    }

    @Test
    fun `SetComputeUnitPrice is borsh - discriminator 3 then a little-endian u64`() {
        // Hand-encoded because the app now builds this instruction itself rather than letting
        // WalletCore append one. Getting the width or the endianness wrong would price the fee at
        // some other number entirely, and nothing downstream would say so.
        assertEquals(
            listOf(3, 0x20, 0x4E, 0, 0, 0, 0, 0, 0),
            KaminoComputeBudget.setUnitPriceData(BigInteger.valueOf(20_000)).map {
                it.toInt() and 0xFF
            },
        )

        assertEquals(
            listOf(3, 0, 0, 0, 0, 0, 0, 0, 0),
            KaminoComputeBudget.setUnitPriceData(BigInteger.ZERO).map { it.toInt() and 0xFF },
        )

        // The full u64 range is representable; one past it is not a price the instruction can
        // carry.
        val maxU64 = BigInteger.TWO.pow(64).subtract(BigInteger.ONE)
        assertEquals(
            List(9) { if (it == 0) 3 else 0xFF },
            KaminoComputeBudget.setUnitPriceData(maxU64).map { it.toInt() and 0xFF },
        )
        assertThrows<IllegalArgumentException> {
            KaminoComputeBudget.setUnitPriceData(maxU64.add(BigInteger.ONE))
        }
        assertThrows<IllegalArgumentException> {
            KaminoComputeBudget.setUnitPriceData(BigInteger.valueOf(-1))
        }
    }

    @Test
    fun `the budget is read back out of the instructions it was encoded into`() {
        val budget =
            KaminoComputeBudget.readFrom(
                computeBudgetInstructions(solVault, KaminoAction.DEPOSIT, price = MILLION)
            )

        assertEquals(
            KaminoPriorityFee(limit = BigInteger.valueOf(350_000), price = MILLION),
            budget,
        )
    }

    @Test
    fun `instructions carrying no compute budget read as none`() {
        // Distinct from unreadable: the caller has to be able to tell "there is no budget" from
        // "there is one and it does not parse", because only the second contradicts a payload.
        assertNull(
            KaminoComputeBudget.readFrom(
                listOf(KaminoTxInstruction(KaminoVaultRegistry.PROGRAM_ID, byteArrayOf(1)))
            )
        )
    }

    @Test
    fun `a truncated argument is unreadable rather than read from its prefix`() {
        // A shorter or longer instruction is not the one it claims to be, and reading a partial
        // little-endian argument would answer some smaller number with full confidence.
        assertEquals(
            KaminoComputeBudget.MALFORMED,
            KaminoComputeBudget.readFrom(
                listOf(
                    KaminoTxInstruction(KaminoComputeBudget.PROGRAM_ID, byteArrayOf(2, 0x20, 0x4E)),
                    KaminoTxInstruction(
                        KaminoComputeBudget.PROGRAM_ID,
                        KaminoComputeBudget.setUnitPriceData(MILLION),
                    ),
                )
            ),
        )
    }

    @Test
    fun `a second price instruction is unreadable rather than resolved to one of them`() {
        val duplicated =
            computeBudgetInstructions(solVault, KaminoAction.DEPOSIT) +
                KaminoTxInstruction(
                    KaminoComputeBudget.PROGRAM_ID,
                    KaminoComputeBudget.setUnitPriceData(MILLION),
                )

        assertEquals(KaminoComputeBudget.MALFORMED, KaminoComputeBudget.readFrom(duplicated))
    }

    @Test
    fun `the unreadable marker matches no budget a payload could record`() {
        // It is compared for equality against what the payload claims, so it has to be a value no
        // legitimate claim can take — the payload's fields are unsigned.
        assertTrue(KaminoComputeBudget.MALFORMED.limit.signum() < 0)
        assertTrue(KaminoComputeBudget.MALFORMED.price.signum() < 0)
    }

    private val MILLION: BigInteger = BigInteger.valueOf(1_000_000)
}
