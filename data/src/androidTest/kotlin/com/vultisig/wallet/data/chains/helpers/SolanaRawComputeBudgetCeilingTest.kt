package com.vultisig.wallet.data.chains.helpers

import android.util.Base64
import com.vultisig.wallet.data.blockchain.solana.SolanaSignatureEnvelope
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoFixtures
import com.vultisig.wallet.data.common.toHexByteArray
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The raw dApp-transaction signing path enforces the same priority-fee ceilings as the structured
 * send path. A real Kamino relayed deposit (which carries no ComputeBudget instructions) and a
 * transaction whose ComputeBudget sits at the ceilings are accepted; one whose
 * `SetComputeUnitPrice` or `SetComputeUnitLimit` exceeds them is refused.
 */
class SolanaRawComputeBudgetCeilingTest {

    private val check =
        SolanaHelper::class
            .java
            .getDeclaredMethod(
                "requireRawComputeBudgetWithinCeiling",
                SolanaSignatureEnvelope::class.java,
            )
            .apply { isAccessible = true }

    private fun run(transaction: ByteArray) =
        check.invoke(SolanaHelper(vaultHexPublicKey = ""), SolanaSignatureEnvelope.parse(transaction))

    @Test
    fun `a real relayed deposit is accepted`() {
        run(Base64.decode(KaminoFixtures.DEPOSIT, Base64.DEFAULT))
    }

    @Test
    fun `a compute budget at the ceilings is accepted`() {
        run(
            transactionWithComputeBudget(
                unitPrice = SOLANA_MAX_PRIORITY_FEE_PRICE,
                unitLimit = SOLANA_MAX_COMPUTE_UNIT_LIMIT,
            )
        )
    }

    @Test
    fun `an inflated priority-fee price in a raw transaction is refused`() {
        assertRefused(
            transactionWithComputeBudget(
                unitPrice = SOLANA_MAX_PRIORITY_FEE_PRICE + 1,
                unitLimit = SOLANA_PRIORITY_FEE_LIMIT.toLong(),
            ),
            "priority-fee price",
        )
    }

    @Test
    fun `an inflated compute-unit limit in a raw transaction is refused`() {
        assertRefused(
            transactionWithComputeBudget(
                unitPrice = SOLANA_PRIORITY_FEE_PRICE,
                unitLimit = SOLANA_MAX_COMPUTE_UNIT_LIMIT + 1,
            ),
            "compute-unit limit",
        )
    }

    private fun assertRefused(transaction: ByteArray, reason: String) {
        val e = assertFailsWith<InvocationTargetException> { run(transaction) }
        val cause = assertIs<IllegalArgumentException>(e.cause)
        assertTrue(cause.message.orEmpty().contains(reason), cause.message)
    }

    /**
     * A one-signer legacy transaction whose only instructions are `SetComputeUnitLimit` and
     * `SetComputeUnitPrice` — the shape a dApp prepends to the instructions it actually wants.
     */
    private fun transactionWithComputeBudget(unitPrice: Long, unitLimit: Long): ByteArray {
        val feePayer = ByteArray(32) { 0x11 }
        val computeBudget =
            "0306466fe5211732ffecadba72c39be7bc8ce5bbc5f7126b2c439b3a40000000".toHexByteArray()
        val blockhash = ByteArray(32) { 0x22 }
        val setLimit = byteArrayOf(2) + littleEndian(unitLimit, 4)
        val setPrice = byteArrayOf(3) + littleEndian(unitPrice, 8)
        val message =
            byteArrayOf(1, 0, 1) + // header: 1 signer, 0 readonly-signed, 1 readonly-unsigned
                byteArrayOf(2) +
                feePayer +
                computeBudget +
                blockhash +
                byteArrayOf(2) + // 2 instructions, program index 1, no accounts
                byteArrayOf(1, 0, setLimit.size.toByte()) +
                setLimit +
                byteArrayOf(1, 0, setPrice.size.toByte()) +
                setPrice
        return byteArrayOf(1) + ByteArray(64) + message
    }

    private fun littleEndian(value: Long, length: Int): ByteArray =
        ByteArray(length) { i -> (value ushr (8 * i)).toByte() }
}
