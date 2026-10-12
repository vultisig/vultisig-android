package com.vultisig.wallet.data.chains.helpers

import java.lang.reflect.InvocationTargetException
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Solana

/**
 * `applyPriorityFee` bounds the Solana priority-fee price and compute-unit limit on every signer,
 * including a co-signer rebuilding a relayed transaction, so a compromised initiator can't inflate
 * them to burn the balance as a priority fee. An out-of-range value is refused rather than clamped
 * (a clamped value would diverge from an unpatched initiator and fail the ceremony opaquely). The
 * method builds only protobuf (no WalletCore JNI), so it is exercised headlessly via reflection.
 */
class SolanaPriorityFeeCapTest {

    private val apply =
        SolanaHelper::class
            .java
            .getDeclaredMethod(
                "applyPriorityFee",
                Solana.SigningInput.Builder::class.java,
                BigInteger::class.java,
                BigInteger::class.java,
            )
            .apply { isAccessible = true }

    private fun priceAndLimit(price: BigInteger, limit: BigInteger): Pair<Long, Int> {
        val builder = Solana.SigningInput.newBuilder()
        apply.invoke(SolanaHelper(vaultHexPublicKey = ""), builder, price, limit)
        return builder.priorityFeePrice.price to builder.priorityFeeLimit.limit
    }

    /** Reflection wraps the thrown IllegalArgumentException; unwrap it. */
    private fun assertRejected(price: BigInteger, limit: BigInteger, fragment: String) {
        val e =
            assertThrows(InvocationTargetException::class.java) { priceAndLimit(price, limit) }
        val cause = e.cause
        assertTrue(
            cause is IllegalArgumentException && cause.message?.contains(fragment) == true,
            cause?.message.orEmpty(),
        )
    }

    @Test
    fun `an inflated price is refused`() {
        assertRejected(
            price = BigInteger.valueOf(1_000_000_000_000L),
            limit = BigInteger.valueOf(200_000L),
            fragment = "priority-fee price",
        )
    }

    @Test
    fun `an inflated compute-unit limit is refused`() {
        assertRejected(
            price = BigInteger.valueOf(5_000_000L),
            limit = BigInteger.valueOf(10_000_000L),
            fragment = "compute-unit limit",
        )
    }

    @Test
    fun `a price below the floor is raised to the floor`() {
        val (price, _) =
            priceAndLimit(price = BigInteger.ZERO, limit = BigInteger.valueOf(100_000L))
        assertEquals(SOLANA_PRIORITY_FEE_PRICE, price)
    }

    @Test
    fun `a normal price and limit pass through unchanged`() {
        val (price, limit) =
            priceAndLimit(
                price = BigInteger.valueOf(5_000_000L),
                limit = BigInteger.valueOf(200_000L),
            )
        assertEquals(5_000_000L, price)
        assertEquals(200_000, limit)
    }

    @Test
    fun `the exact ceilings are accepted`() {
        val (price, limit) =
            priceAndLimit(
                price = BigInteger.valueOf(SOLANA_MAX_PRIORITY_FEE_PRICE),
                limit = BigInteger.valueOf(SOLANA_MAX_COMPUTE_UNIT_LIMIT),
            )
        assertEquals(SOLANA_MAX_PRIORITY_FEE_PRICE, price)
        assertEquals(SOLANA_MAX_COMPUTE_UNIT_LIMIT, limit.toLong())
    }
}
