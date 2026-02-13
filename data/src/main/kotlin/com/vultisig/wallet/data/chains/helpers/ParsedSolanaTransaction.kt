package com.vultisig.wallet.data.chains.helpers

import wallet.core.jni.CoinType
import wallet.core.jni.TransactionDecoder
import wallet.core.jni.proto.Solana

data class ParsedSolanaTransaction(
    val instructions: List<ParsedInstruction>
) {
    data class ParsedInstruction(
        val programId: String,
        val programName: String?,
        val instructionType: String?,
        val accountsCount: Int,
        val dataLength: Int
    )
}

object SolanaTransactionParser {

    // Known Solana programs
    private val knownPrograms = mapOf(
        "11111111111111111111111111111111" to "System Program",
        "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA" to "Token Program",
        "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb" to "Token-2022 Program",
        "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL" to "Associated Token Program",
        "ComputeBudget111111111111111111111111111111" to "Compute Budget Program"
    )

    fun parse(base64Transaction: String): ParsedSolanaTransaction {
        val txData = android.util.Base64.decode(base64Transaction, android.util.Base64.DEFAULT)

        val decodedData = TransactionDecoder.decode(CoinType.SOLANA, txData)
        val decodingOutput = Solana.DecodingTransactionOutput.parseFrom(decodedData)

        if (!decodingOutput.hasTransaction()) {
            error("Invalid transaction format")
        }

        val transaction = decodingOutput.transaction

        val instructions: List<Solana.RawMessage.Instruction>
        val accountKeys: List<String>

        when {
            transaction.hasV0() -> {
                val v0Message = transaction.v0
                instructions = v0Message.instructionsList
                accountKeys = v0Message.accountKeysList
            }
            transaction.hasLegacy() -> {
                val legacyMessage = transaction.legacy
                instructions = legacyMessage.instructionsList
                accountKeys = legacyMessage.accountKeysList
            }
            else -> error("Invalid transaction format")
        }

        val parsedInstructions = instructions.map { instruction ->
            val programIndex = instruction.programId.toInt()
            val programId = accountKeys.getOrNull(programIndex) ?: "Unknown"
            val programName = getKnownProgramName(programId)
            val instructionType = getInstructionType(programId, instruction.programData.toByteArray())

            ParsedSolanaTransaction.ParsedInstruction(
                programId = programId,
                programName = programName,
                instructionType = instructionType,
                accountsCount = instruction.accountsCount,
                dataLength = instruction.programData.size()
            )
        }

        return ParsedSolanaTransaction(
            instructions = parsedInstructions
        )
    }

    fun getKnownProgramName(programId: String): String? {
        return knownPrograms[programId]
    }

    private fun getInstructionType(programId: String, instructionData: ByteArray): String? {
        if (instructionData.isEmpty()) return null

        val discriminator = instructionData[0].toInt() and 0xFF

        if (programId == "11111111111111111111111111111111") {
            return when (discriminator) {
                0 -> "Create Account"
                2 -> "Transfer"
                3 -> "Assign"
                4 -> "Create Account With Seed"
                9 -> "Transfer With Seed"
                else -> "System ($discriminator)"
            }
        }

        if (programId == "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA") {
            return when (discriminator) {
                0 -> "Initialize Mint"
                1 -> "Initialize Account"
                3 -> "Transfer"
                7 -> "Mint To"
                8 -> "Burn"
                9 -> "Close Account"
                12 -> "Transfer Checked"
                else -> "Token ($discriminator)"
            }
        }

        if (programId == "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb") {
            return when (discriminator) {
                0 -> "Initialize Mint"
                1 -> "Initialize Account"
                3 -> "Transfer"
                7 -> "Mint To"
                8 -> "Burn"
                9 -> "Close Account"
                12 -> "Transfer Checked"
                else -> "Token-2022 ($discriminator)"
            }
        }

        if (programId == "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL") {
            return "Create Associated Token Account"
        }

        if (programId == "ComputeBudget111111111111111111111111111111") {
            return when (discriminator) {
                0 -> "Request Heap Frame"
                1 -> "Set Compute Unit Limit"
                2 -> "Set Compute Unit Price"
                else -> "Compute Budget ($discriminator)"
            }
        }

        return null
    }
}