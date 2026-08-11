package com.vultisig.wallet.data.blockchain.solana.kamino

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class KaminoTransactionValidatorTest {

    private val vault = KaminoVaultRegistry.STEAKHOUSE_USDC

    private fun ix(programId: String, data: ByteArray = ByteArray(0)) =
        KaminoTxInstruction(programId, data)

    private val memo =
        ix(KaminoAttributionMemo.MEMO_PROGRAM_ID, KaminoAttributionMemo.TAG.toByteArray())

    /** The shape a prepared deposit actually has: ATA, kVault, two farms, compute budget, memo. */
    private fun depositInstructions(memoInstruction: KaminoTxInstruction? = memo) =
        listOfNotNull(
            ix("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"),
            ix(KaminoVaultRegistry.PROGRAM_ID, byteArrayOf(1, 2, 3)),
            ix(KaminoVaultRegistry.FARMS_PROGRAM_ID),
            ix(KaminoVaultRegistry.FARMS_PROGRAM_ID),
            ix("ComputeBudget111111111111111111111111111111"),
            memoInstruction,
        )

    private fun rejection(instructions: List<KaminoTxInstruction>): String =
        assertThrows<KaminoTransactionRejected> {
                KaminoTransactionValidator.validate(instructions, vault, KaminoAction.DEPOSIT)
            }
            .message
            .orEmpty()

    @Test
    fun `a well-formed deposit passes`() {
        assertDoesNotThrow {
            KaminoTransactionValidator.validate(depositInstructions(), vault, KaminoAction.DEPOSIT)
        }
    }

    @Test
    fun `a withdraw without the farms instructions passes`() {
        assertDoesNotThrow {
            KaminoTransactionValidator.validate(
                listOf(
                    ix(KaminoVaultRegistry.PROGRAM_ID, byteArrayOf(9)),
                    ix("ComputeBudget111111111111111111111111111111"),
                    memo,
                ),
                vault,
                KaminoAction.WITHDRAW,
            )
        }
    }

    @Test
    fun `an unknown program is refused`() {
        // The transaction is built by Kamino, so an instruction the app cannot name is exactly what
        // this check exists to stop.
        val reason =
            rejection(
                depositInstructions().dropLast(1) +
                    ix("9xQeWvG816bUx9EPjHmaT23yvVM2ZWbrrpZb9PusVFin") +
                    memo
            )
        assertTrue(reason.contains("unexpected program"), reason)
    }

    @Test
    fun `a transaction that never touches the kVault program is refused`() {
        val reason = rejection(listOf(ix("11111111111111111111111111111111"), memo))
        assertTrue(reason.contains("never invokes the kVault program"), reason)
    }

    @Test
    fun `a missing memo is refused`() {
        val reason = rejection(depositInstructions(memoInstruction = null))
        assertTrue(reason.contains("memo is missing"), reason)
    }

    @Test
    fun `two memos are refused even when both carry the tag`() {
        // Appending twice is a caller bug; signing it would double-count the attribution.
        val reason = rejection(depositInstructions() + memo)
        assertTrue(reason.contains("exactly one memo"), reason)
    }

    @Test
    fun `a memo carrying anything other than the tag is refused`() {
        // An arbitrary memo riding along on a signed transaction is a channel the app did not open.
        listOf("vs ", " vs", "VS", "vsx", "", "vultisig").forEach { payload ->
            val reason =
                rejection(
                    depositInstructions(
                        memoInstruction =
                            ix(KaminoAttributionMemo.MEMO_PROGRAM_ID, payload.toByteArray())
                    )
                )
            assertTrue(reason.contains("unexpected data"), "payload '$payload': $reason")
        }
    }

    @Test
    fun `a memo that is not last is refused`() {
        val reason =
            rejection(
                listOf(
                    ix(KaminoVaultRegistry.PROGRAM_ID),
                    memo,
                    ix("ComputeBudget111111111111111111111111111111"),
                )
            )
        assertTrue(reason.contains("final instruction"), reason)
    }

    @Test
    fun `an empty transaction is refused`() {
        assertTrue(rejection(emptyList()).contains("no instructions"))
    }

    @Test
    fun `a vault outside the registry is refused before anything else is checked`() {
        val uncurated = vault.copy(address = "2Z6C84pCc2ri8t39jvRCXnTGFQqUJf1mMpUMtpeFfhyB")
        val reason =
            assertThrows<KaminoTransactionRejected> {
                    KaminoTransactionValidator.validate(
                        depositInstructions(),
                        uncurated,
                        KaminoAction.DEPOSIT,
                    )
                }
                .message
                .orEmpty()
        assertTrue(reason.contains("not a vault this app transacts with"), reason)
    }

    @Test
    fun `instruction value semantics do not depend on array identity`() {
        // Guards the hand-written equals: without it, structurally identical instructions would
        // compare unequal and the memo checks would silently stop matching.
        assertEquals(
            ix(KaminoAttributionMemo.MEMO_PROGRAM_ID, byteArrayOf(0x76, 0x73)),
            ix(KaminoAttributionMemo.MEMO_PROGRAM_ID, byteArrayOf(0x76, 0x73)),
        )
    }
}
