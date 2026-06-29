package com.vultisig.wallet.data.api

import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Pins the wallet-core `insertInstruction` JSON for the idempotent create-fee-ATA instruction. A
 * wrong account order, signer/writable flag, program id, or data byte would build a transaction
 * that fails on-chain, so this guards the exact SPL Associated Token Account layout.
 */
class JupiterFeeAtaInstructionTest {

    private val payer = "8iqhrtBzMcYLR6c6FkzeoMHibedYDkHvLKnX2ArNie5z"
    private val ata = "tigMQDjwCNAzNndtiX93ZK1p71XaKTTRrQ8mfyp39LS"
    private val owner = "FeeOwner111111111111111111111111111111111"
    private val mint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    private val tokenProgram = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

    private val associatedTokenProgram = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
    private val systemProgram = "11111111111111111111111111111111"

    @Test
    fun `builds the SPL create-idempotent ATA instruction with the canonical layout`() {
        val element =
            Json.parseToJsonElement(
                    createIdempotentAtaInstructionJson(
                        feePayer = payer,
                        ata = ata,
                        owner = owner,
                        mint = mint,
                        tokenProgramId = tokenProgram,
                    )
                )
                .jsonObject

        assertEquals(associatedTokenProgram, element["programId"]!!.jsonPrimitive.content)
        // base58 of the single CreateIdempotent discriminator byte [1].
        assertEquals("2", element["data"]!!.jsonPrimitive.content)

        val accounts = element["accounts"]!!.jsonArray
        assertEquals(6, accounts.size)

        // payer: signer + writable (funds the rent)
        accounts[0].jsonObject.let {
            assertEquals(payer, it["pubkey"]!!.jsonPrimitive.content)
            assertEquals(true, it["isSigner"]!!.jsonPrimitive.boolean)
            assertEquals(true, it["isWritable"]!!.jsonPrimitive.boolean)
        }
        // associated token account: writable, not a signer
        accounts[1].jsonObject.let {
            assertEquals(ata, it["pubkey"]!!.jsonPrimitive.content)
            assertEquals(false, it["isSigner"]!!.jsonPrimitive.boolean)
            assertEquals(true, it["isWritable"]!!.jsonPrimitive.boolean)
        }
        // owner, mint, system program, token program: read-only, not signers
        listOf(2 to owner, 3 to mint, 4 to systemProgram, 5 to tokenProgram).forEach { (i, key) ->
            accounts[i].jsonObject.let {
                assertEquals(key, it["pubkey"]!!.jsonPrimitive.content)
                assertEquals(false, it["isSigner"]!!.jsonPrimitive.boolean)
                assertEquals(false, it["isWritable"]!!.jsonPrimitive.boolean)
            }
        }
    }
}
