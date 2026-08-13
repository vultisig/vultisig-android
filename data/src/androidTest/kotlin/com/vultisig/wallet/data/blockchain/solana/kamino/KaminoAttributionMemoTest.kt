package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.WalletCoreNative
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import wallet.core.jni.Base58
import wallet.core.jni.CoinType
import wallet.core.jni.SolanaTransaction
import wallet.core.jni.TransactionDecoder
import wallet.core.jni.proto.Solana

/**
 * Golden coverage for the `vs` attribution memo against a real Kamino deposit.
 *
 * The fixture is a live `POST /ktx/kvault/deposit` response for the Steakhouse USDC vault: a v0
 * versioned transaction whose four instructions address 9 static account keys plus 18 loaded
 * through one address lookup table. Lookup-derived accounts are indexed immediately after the
 * static keys, so appending the memo program as a static key shifts every one of them by one. Those
 * shifts are the whole risk in this operation — an off-by-one repoints an existing instruction at a
 * different account while every other check still passes — so they are asserted here index by index
 * rather than trusted.
 *
 * Runs instrumented because both the insertion and the decode are WalletCore JNI.
 */
class KaminoAttributionMemoTest {

    @Before
    fun loadWalletCore() {
        WalletCoreNative.ensureLoaded()
    }

    /**
     * Steakhouse USDC deposit of 1 USDC, unsigned (a zeroed 64-byte signature envelope).
     * Instructions: create the shares ATA, `kVault::deposit`, then two `farms` instructions that
     * stake the shares.
     */
    private val depositTransaction =
        "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACA" +
            "AQAFCX//JB25KLX6LxwUe5Pw/FGXqeETK7Jj6GQYPDbOSuemOEsQm7A2IghRdbzU0ar6Q7dIhEJA6xfcD32X" +
            "M4YwfPSF38N5IeLVVX5/3BJfld0IR08X1RB7fe6uQkeVw/nk3uVtPK//R3XanFktvqbKIbsjaArJEZzlqWvc" +
            "axKftHFhAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP2mzp9mGY3YcNiphvMGdcRvg+L1KDSHlS" +
            "9dUFY+PveoyXJY9OJInxuz0QKRSODYMLWhOZ2v8QhASOe9jb6fhZ2LAQF2PT5R8SbmFW3oXejGEwWbhEaNDa" +
            "P+iioiUcxwEE2Qrx24k57DX/lNlkDVfcwyeUuz4btm/TroSahNzblDJCgcwkFVHRKh2nMxb4pfSsIMN/m4f+" +
            "R1HblBX15CRABAYGAAMACQQaAQEIFQARDRYUCQEDGBoaBQgQDwoMFRMXEhDyI8aJUuHytkBCDwAAAAAABwgA" +
            "AAAAAgsEGQhvEbn6PHom/gcIAAILDgMJBxoQzrDKEsjRs2z//////////wGC6dRmw0Z9LrKw2cy+tHvb9JXu" +
            "HBu0d9Dar60DX6fr5AkFNTElLzITCQEJJxUECwI3BwYD"

    private fun decode(base64Transaction: String): Solana.RawMessage.MessageV0 {
        val decoded =
            TransactionDecoder.decode(
                CoinType.SOLANA,
                Base64.getDecoder().decode(base64Transaction),
            )
        val output = Solana.DecodingTransactionOutput.parseFrom(decoded)
        assertTrue("fixture must decode", output.hasTransaction())
        assertTrue("fixture must be a v0 versioned message", output.transaction.hasV0())
        return output.transaction.v0
    }

    /** Resolves an instruction's program index against the message's static account keys. */
    private fun programOf(
        message: Solana.RawMessage.MessageV0,
        instruction: Solana.RawMessage.Instruction,
    ): String = message.getAccountKeys(instruction.programId)

    @Test
    fun fixture_is_the_shape_this_test_reasons_about() {
        val original = decode(depositTransaction)

        assertEquals("static account keys", 9, original.accountKeysCount)
        assertEquals("instructions", 4, original.instructionsCount)
        assertEquals("address lookup tables", 1, original.addressTableLookupsCount)
        assertEquals("readonly unsigned accounts", 5, original.header.numReadonlyUnsignedAccounts)
        assertEquals("required signatures", 1, original.header.numRequiredSignatures)

        // The deposit stakes into the farm, so the shares never become a wallet token balance.
        val programs = original.instructionsList.map { programOf(original, it) }
        assertEquals(
            listOf(
                "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
                KaminoVaultRegistry.PROGRAM_ID,
                KaminoVaultRegistry.FARMS_PROGRAM_ID,
                KaminoVaultRegistry.FARMS_PROGRAM_ID,
            ),
            programs,
        )

        // At least one instruction must address a lookup-loaded account, or the shift this test
        // exists to verify would never be exercised.
        val highest = original.instructionsList.flatMap { it.accountsList }.maxOrNull() ?: 0
        assertTrue(
            "fixture must address lookup-loaded accounts (highest index $highest)",
            highest >= original.accountKeysCount,
        )
    }

    @Test
    fun append_adds_exactly_one_memo_instruction_carrying_the_tag() {
        val updated = decode(KaminoAttributionMemo.append(depositTransaction))

        assertEquals("one instruction added", 5, updated.instructionsCount)

        val memos =
            updated.instructionsList.filter {
                programOf(updated, it) == KaminoAttributionMemo.MEMO_PROGRAM_ID
            }
        assertEquals("exactly one memo", 1, memos.size)

        val memo = memos.single()
        assertEquals(
            "memo is appended last",
            updated.instructionsCount - 1,
            updated.instructionsList.indexOf(memo),
        )
        assertEquals("memo takes no accounts", 0, memo.accountsCount)
        assertEquals(
            "memo data is the bare tag",
            KaminoAttributionMemo.TAG,
            memo.programData.toByteArray().toString(Charsets.US_ASCII),
        )
        assertEquals("memo adds no signer", 1, updated.header.numRequiredSignatures)
    }

    @Test
    fun append_registers_the_memo_program_as_a_trailing_readonly_unsigned_key() {
        val original = decode(depositTransaction)
        val updated = decode(KaminoAttributionMemo.append(depositTransaction))

        assertEquals("one static key added", 10, updated.accountKeysCount)
        assertEquals(
            "memo program is the new last static key",
            KaminoAttributionMemo.MEMO_PROGRAM_ID,
            updated.getAccountKeys(updated.accountKeysCount - 1),
        )
        assertEquals(
            "readonly unsigned count follows the new key",
            original.header.numReadonlyUnsignedAccounts + 1,
            updated.header.numReadonlyUnsignedAccounts,
        )
        assertEquals(
            "pre-existing static keys are untouched and in order",
            original.accountKeysList,
            updated.accountKeysList.dropLast(1),
        )
        assertEquals(
            "lookup tables are untouched",
            original.addressTableLookupsList,
            updated.addressTableLookupsList,
        )
    }

    @Test
    fun append_shifts_every_lookup_derived_index_and_leaves_static_ones_alone() {
        val original = decode(depositTransaction)
        val updated = decode(KaminoAttributionMemo.append(depositTransaction))

        val insertedAt = original.accountKeysCount // 9 — the memo program's new slot
        val originalInstructions = original.instructionsList
        val carriedOver = updated.instructionsList.dropLast(1) // drop the memo

        assertEquals("original instructions survive", originalInstructions.size, carriedOver.size)

        originalInstructions.forEachIndexed { position, before ->
            val after = carriedOver[position]

            val expectedAccounts = before.accountsList.map { if (it >= insertedAt) it + 1 else it }
            assertEquals(
                "instruction $position account indices",
                expectedAccounts,
                after.accountsList,
            )
            assertEquals(
                "instruction $position program index",
                if (before.programId >= insertedAt) before.programId + 1 else before.programId,
                after.programId,
            )
            assertEquals("instruction $position data", before.programData, after.programData)

            // The point of the shift: each instruction must still invoke the same program and
            // resolve to the same accounts once the new key table is applied.
            assertEquals(
                "instruction $position still invokes the same program",
                programOf(original, before),
                programOf(updated, after),
            )
        }

        // Guard the assertion itself: a fixture whose indices all sat below the insertion point
        // would make the shift check vacuous.
        assertNotEquals(
            "at least one index must actually move",
            originalInstructions.map { it.accountsList },
            carriedOver.map { it.accountsList },
        )
    }

    @Test
    fun memo_data_is_base58_encoded_and_the_transaction_stays_within_the_size_limit() {
        // Pinned to the literal, not derived from the constant. Every other assertion here reads
        // TAG, so a drift from `vs` to any other valid two-byte string would leave this whole suite
        // agreeing with itself and green while the attribution it exists for matched nothing.
        assertEquals("vs", KaminoAttributionMemo.TAG)
        assertArrayEquals(byteArrayOf(0x76, 0x73), KaminoAttributionMemo.TAG.toByteArray())

        // WalletCore's JSON instruction format encodes `data` as base58, not base64. Pinning the
        // encoded form keeps the two on-chain bytes identical across platforms.
        assertEquals("A1p", Base58.encodeNoCheck(KaminoAttributionMemo.TAG.toByteArray()))

        val before = Base64.getDecoder().decode(depositTransaction).size
        val after =
            Base64.getDecoder().decode(KaminoAttributionMemo.append(depositTransaction)).size

        assertTrue("memo must grow the transaction", after > before)
        assertTrue(
            "signed transaction must stay within the v0 packet limit (was $after bytes)",
            after <= 1232,
        )
    }

    @Test
    fun append_is_idempotent_in_shape_but_never_silently_duplicates_the_tag() {
        // Appending twice is a caller bug, not something to paper over: the result must still be
        // detectable as carrying two memos so validation can refuse it.
        val twice =
            decode(KaminoAttributionMemo.append(KaminoAttributionMemo.append(depositTransaction)))
        val memos =
            twice.instructionsList.count {
                programOf(twice, it) == KaminoAttributionMemo.MEMO_PROGRAM_ID
            }
        assertEquals("a second append is visible, not swallowed", 2, memos)
        assertEquals("the program key is reused rather than duplicated", 10, twice.accountKeysCount)
    }

    @Test
    fun append_fails_loudly_when_walletcore_cannot_produce_a_transaction() {
        // The FFI collapses every failure into a null return, so the helper must convert that into
        // a throw. Signing the unmodified transaction would silently drop the attribution.
        assertNull(
            "precondition: WalletCore returns null for a malformed transaction",
            SolanaTransaction.insertInstruction("not-a-transaction", -1, "{}"),
        )
        assertThrows(IllegalStateException::class.java) {
            KaminoAttributionMemo.append("not-a-transaction")
        }
    }
}
