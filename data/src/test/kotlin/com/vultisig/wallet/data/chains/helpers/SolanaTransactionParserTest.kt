package com.vultisig.wallet.data.chains.helpers

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class SolanaTransactionParserTest {

    private val systemProgramId = "11111111111111111111111111111111"
    private val computeBudgetProgramId = "ComputeBudget111111111111111111111111111111"
    private val tokenProgramId = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    private val associatedTokenProgramId = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"

    private fun instructionType(programId: String, discriminator: Int): String? =
        SolanaTransactionParser.getInstructionType(programId, byteArrayOf(discriminator.toByte()))

    @Test
    fun `System Program labels match the real SystemInstruction enum for every known discriminator`() {
        val expectedLabelsByDiscriminator =
            mapOf(
                0 to "Create Account",
                1 to "Assign",
                2 to "Transfer",
                3 to "Create Account With Seed",
                4 to "Advance Nonce Account",
                5 to "Withdraw Nonce Account",
                6 to "Initialize Nonce Account",
                7 to "Authorize Nonce Account",
                8 to "Allocate",
                9 to "Allocate With Seed",
                10 to "Assign With Seed",
                11 to "Transfer With Seed",
            )

        expectedLabelsByDiscriminator.forEach { (discriminator, expectedLabel) ->
            assertEquals(
                expectedLabel,
                instructionType(systemProgramId, discriminator),
                "discriminator $discriminator",
            )
        }
    }

    @Test
    fun `System Program falls back to a numeric label for an unmapped discriminator`() {
        assertEquals("System (12)", instructionType(systemProgramId, 12))
    }

    @Test
    fun `Compute Budget labels match the real ComputeBudgetInstruction enum for every known discriminator`() {
        val expectedLabelsByDiscriminator =
            mapOf(
                0 to "Unused",
                1 to "Request Heap Frame",
                2 to "Set Compute Unit Limit",
                3 to "Set Compute Unit Price",
            )

        expectedLabelsByDiscriminator.forEach { (discriminator, expectedLabel) ->
            assertEquals(
                expectedLabel,
                instructionType(computeBudgetProgramId, discriminator),
                "discriminator $discriminator",
            )
        }
    }

    @Test
    fun `Compute Budget SetComputeUnitPrice is no longer swallowed by the numeric fallback`() {
        assertEquals("Set Compute Unit Price", instructionType(computeBudgetProgramId, 3))
    }

    @Test
    fun `Compute Budget falls back to a numeric label for an unmapped discriminator`() {
        assertEquals("Compute Budget (4)", instructionType(computeBudgetProgramId, 4))
    }

    @Test
    fun `other program label tables are unaffected by the System and Compute Budget fix`() {
        assertEquals("Initialize Mint", instructionType(tokenProgramId, 0))
        assertEquals("Transfer Checked", instructionType(tokenProgramId, 12))
        assertEquals(
            "Create Associated Token Account",
            instructionType(associatedTokenProgramId, 0),
        )
    }

    @Test
    fun `empty instruction data yields no instruction type`() {
        assertNull(SolanaTransactionParser.getInstructionType(systemProgramId, byteArrayOf()))
    }

    @Test
    fun `unknown program id yields no instruction type`() {
        assertNull(instructionType("UnknownProgram11111111111111111111111111", 0))
    }
}
