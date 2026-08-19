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

    /**
     * Most rules do not care who pays, so the fee payer defaults to a benign value; the fee-payer
     * test sets it explicitly.
     */
    private fun decoded(
        instructions: List<KaminoTxInstruction>,
        feePayer: String? = DEFAULT_FEE_PAYER,
    ) = KaminoDecodedTransaction(feePayer = feePayer, instructions = instructions)

    private fun rejection(instructions: List<KaminoTxInstruction>): String =
        assertThrows<KaminoTransactionRejected> {
                KaminoTransactionValidator.validate(
                    decoded(instructions),
                    vault,
                    KaminoAction.DEPOSIT,
                )
            }
            .message
            .orEmpty()

    @Test
    fun `a well-formed deposit passes`() {
        assertDoesNotThrow {
            KaminoTransactionValidator.validate(
                decoded(depositInstructions()),
                vault,
                KaminoAction.DEPOSIT,
            )
        }
    }

    @Test
    fun `a withdraw without the farms instructions passes`() {
        assertDoesNotThrow {
            KaminoTransactionValidator.validate(
                decoded(
                    listOf(
                        ix(KaminoVaultRegistry.PROGRAM_ID, byteArrayOf(9)),
                        ix("ComputeBudget111111111111111111111111111111"),
                        memo,
                    )
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
        // The tag is matched whole and case-sensitively: a memo that merely starts with it, or that
        // differs only in case, is a different memo that the attribution filter would not count.
        listOf("8k2m", "8k2mz1", "8k2mz ", " 8k2mz", "8K2MZ", "", "vultisig").forEach { payload ->
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
    fun `a memo naming accounts is refused, tag or no tag`() {
        // A memo listing accounts is the Memo program attesting that they signed. Attribution is
        // not
        // that, and the app's own injection names none — so an account list means it came from
        // somewhere else, even when the bytes happen to be right.
        val reason =
            rejection(
                listOf(
                    ix(KaminoVaultRegistry.PROGRAM_ID),
                    KaminoTxInstruction(
                        programId = KaminoAttributionMemo.MEMO_PROGRAM_ID,
                        data = KaminoAttributionMemo.TAG.toByteArray(),
                        accounts = listOf("9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"),
                    ),
                )
            )
        assertTrue(reason.contains("name no accounts"), reason)
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
                        decoded(depositInstructions()),
                        uncurated,
                        KaminoAction.DEPOSIT,
                    )
                }
                .message
                .orEmpty()
        assertTrue(reason.contains("not a vault this app transacts with"), reason)
    }

    @Test
    fun `a smuggled SPL token transfer is refused even though the transaction is otherwise valid`() {
        // The hole that program allow-listing alone leaves: a deposit legitimately needs the Token
        // program, so an added transfer would pass a check that only asks which programs run.
        val reason =
            rejection(
                listOf(
                    ix(KaminoVaultRegistry.PROGRAM_ID),
                    // SPL Token `Transfer` is discriminator 3.
                    ix(TOKEN_PROGRAM, byteArrayOf(3, 0, 0, 0)),
                    memo,
                )
            )
        assertTrue(reason.contains("SPL token instruction 3"), reason)
    }

    @Test
    fun `TransferChecked is refused too, not just the bare transfer`() {
        val reason =
            rejection(
                listOf(
                    ix(KaminoVaultRegistry.PROGRAM_ID),
                    // `TransferChecked` is discriminator 12.
                    ix(TOKEN_PROGRAM, byteArrayOf(12, 0)),
                    memo,
                )
            )
        assertTrue(reason.contains("SPL token instruction 12"), reason)
    }

    @Test
    fun `moving lamports is refused for a token vault and allowed for the wrapped-SOL one`() {
        // System `Transfer` is 2, little-endian over four bytes. Its second account is the
        // destination, which on a real wrap is the wSOL account the vault deposit also names — so
        // the
        // fixture has to say so, or the destination check refuses it for the right reason.
        // Stands in for the address the preparer derives from the signer and the wSOL mint.
        val wrappedSolAccount = "AyY6VCkHfTWdFs7SqBbu6AnCqLUhgzVHBzW3WcJu5Jc8"
        val systemTransfer =
            KaminoTxInstruction(
                programId = "11111111111111111111111111111111",
                data = byteArrayOf(2, 0, 0, 0, 1, 2, 3, 4),
                accounts = listOf(DEFAULT_FEE_PAYER, wrappedSolAccount),
            )
        val kvault =
            KaminoTxInstruction(
                programId = KaminoVaultRegistry.PROGRAM_ID,
                data = byteArrayOf(1),
                accounts = listOf(wrappedSolAccount),
            )
        val instructions = listOf(kvault, systemTransfer, memo)

        val reason = rejection(instructions)
        assertTrue(reason.contains("moves lamports"), reason)

        // The SOL vault has to wrap, so the same instruction is expected there.
        assertDoesNotThrow {
            KaminoTransactionValidator.validate(
                decoded(instructions),
                KaminoVaultRegistry.ALLEZ_SOL,
                KaminoAction.DEPOSIT,
                wrappedSolAccount = wrappedSolAccount,
            )
        }
    }

    @Test
    fun `a transaction authorised by someone other than the wallet is refused`() {
        val signer = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"
        val instructions =
            listOf(
                KaminoTxInstruction(
                    programId = KaminoVaultRegistry.PROGRAM_ID,
                    data = byteArrayOf(1),
                    accounts = listOf("SomeoneElsesAccount11111111111111111111111"),
                ),
                memo,
            )

        val reason =
            assertThrows<KaminoTransactionRejected> {
                    KaminoTransactionValidator.validate(
                        decoded(instructions, feePayer = OTHER_ACCOUNT),
                        vault,
                        KaminoAction.DEPOSIT,
                        signerAddress = signer,
                    )
                }
                .message
                .orEmpty()
        assertTrue(reason.contains("authorised by"), reason)

        // The wallet's own transaction passes.
        assertDoesNotThrow {
            KaminoTransactionValidator.validate(
                decoded(
                    listOf(
                        KaminoTxInstruction(
                            programId = KaminoVaultRegistry.PROGRAM_ID,
                            data = byteArrayOf(1),
                            accounts = listOf(signer),
                        ),
                        memo,
                    ),
                    feePayer = signer,
                ),
                vault,
                KaminoAction.DEPOSIT,
                signerAddress = signer,
            )
        }
    }

    @Test
    fun `a lamport transfer to an address the deposit does not reference is refused`() {
        // Allowed on the wrapped-SOL vault, but not to anywhere: the only lamports a deposit moves
        // go
        // into the wSOL account it then deposits from. A transfer somewhere else is not a wrap.
        val kvault =
            KaminoTxInstruction(
                programId = KaminoVaultRegistry.PROGRAM_ID,
                data = byteArrayOf(1),
                accounts = listOf("AyY6VCkHfTWdFs7SqBbu6AnCqLUhgzVHBzW3WcJu5Jc8"),
            )
        val strayTransfer =
            KaminoTxInstruction(
                programId = "11111111111111111111111111111111",
                data = byteArrayOf(2, 0, 0, 0, 1, 2, 3, 4),
                accounts = listOf(DEFAULT_FEE_PAYER, OTHER_ACCOUNT),
            )

        val reason =
            assertThrows<KaminoTransactionRejected> {
                    KaminoTransactionValidator.validate(
                        decoded(listOf(kvault, strayTransfer, memo)),
                        KaminoVaultRegistry.ALLEZ_SOL,
                        KaminoAction.DEPOSIT,
                        wrappedSolAccount = "AyY6VCkHfTWdFs7SqBbu6AnCqLUhgzVHBzW3WcJu5Jc8",
                    )
                }
                .message
                .orEmpty()
        assertTrue(reason.contains("rather than to the wallet"), reason)
    }

    @Test
    fun `a transaction paid for by another account is refused on the message, not an instruction`() {
        // The earlier check read the first account of the first instruction, which says nothing
        // about
        // who authorises the transaction: this fixture names the wallet there while the message is
        // paid for by someone else.
        val instructions =
            listOf(
                KaminoTxInstruction(
                    programId = KaminoVaultRegistry.PROGRAM_ID,
                    data = byteArrayOf(1),
                    accounts = listOf(DEFAULT_FEE_PAYER),
                ),
                memo,
            )

        val reason =
            assertThrows<KaminoTransactionRejected> {
                    KaminoTransactionValidator.validate(
                        decoded(instructions, feePayer = OTHER_ACCOUNT),
                        vault,
                        KaminoAction.DEPOSIT,
                        signerAddress = DEFAULT_FEE_PAYER,
                    )
                }
                .message
                .orEmpty()
        assertTrue(reason.contains("authorised by"), reason)
    }

    @Test
    fun `a lamport transfer is refused when the destination is only named by the kVault instruction`() {
        // The check this replaced accepted any destination the kVault instruction also named, which
        // a
        // compromised response could satisfy by listing an attacker there — the same response
        // chooses
        // that account list. Only the locally derived wrapped-SOL address is accepted.
        val attacker = OTHER_ACCOUNT
        val kvault =
            KaminoTxInstruction(
                programId = KaminoVaultRegistry.PROGRAM_ID,
                data = byteArrayOf(1),
                accounts = listOf(attacker),
            )
        val transferToAttacker =
            KaminoTxInstruction(
                programId = "11111111111111111111111111111111",
                data = byteArrayOf(2, 0, 0, 0, 1, 2, 3, 4),
                accounts = listOf(DEFAULT_FEE_PAYER, attacker),
            )

        val reason =
            assertThrows<KaminoTransactionRejected> {
                    KaminoTransactionValidator.validate(
                        decoded(listOf(kvault, transferToAttacker, memo)),
                        KaminoVaultRegistry.ALLEZ_SOL,
                        KaminoAction.DEPOSIT,
                        wrappedSolAccount = "AyY6VCkHfTWdFs7SqBbu6AnCqLUhgzVHBzW3WcJu5Jc8",
                    )
                }
                .message
                .orEmpty()
        assertTrue(reason.contains("rather than to the wallet"), reason)
    }

    @Test
    fun `a wrap is refused outright when the expected account could not be derived`() {
        // Unverifiable is not the same as fine: with no derived address there is nothing to compare
        // the destination against.
        val transfer =
            KaminoTxInstruction(
                programId = "11111111111111111111111111111111",
                data = byteArrayOf(2, 0, 0, 0, 1, 2, 3, 4),
                accounts = listOf(DEFAULT_FEE_PAYER, "AyY6VCkHfTWdFs7SqBbu6AnCqLUhgzVHBzW3WcJu5Jc8"),
            )
        val reason =
            assertThrows<KaminoTransactionRejected> {
                    KaminoTransactionValidator.validate(
                        decoded(listOf(ix(KaminoVaultRegistry.PROGRAM_ID), transfer, memo)),
                        KaminoVaultRegistry.ALLEZ_SOL,
                        KaminoAction.DEPOSIT,
                        wrappedSolAccount = null,
                    )
                }
                .message
                .orEmpty()
        assertTrue(reason.contains("could not be derived"), reason)
    }

    @Test
    fun `the withdraw-everything sentinel is refused on any device`() {
        // u64::MAX is what the kVault program reads as "withdraw everything". This app never
        // produces
        // one — the form's maximum sits strictly below the balance so the API cannot answer with
        // it,
        // and an over-request is refused rather than clamped — so a transaction carrying it did not
        // come from here. It is also the one amount whose consequence is the whole position rather
        // than the figure on screen, which is why it is refused rather than merely disclosed.
        val sentinel =
            ByteArray(16).also { data ->
                // 8-byte Anchor discriminator, then the amount little-endian.
                for (i in 8 until 16) data[i] = 0xFF.toByte()
            }
        val reason = rejection(listOf(ix(KaminoVaultRegistry.PROGRAM_ID, sentinel), memo))
        assertTrue(reason.contains("withdraw-everything sentinel"), reason)
    }

    @Test
    fun `an ordinary amount one below the sentinel is allowed`() {
        // The guard must key on the sentinel exactly, not on "a large amount".
        val justUnder =
            ByteArray(16).also { data ->
                for (i in 8 until 15) data[i] = 0xFF.toByte()
                data[15] = 0xFE.toByte()
            }
        assertDoesNotThrow {
            KaminoTransactionValidator.validate(
                decoded(listOf(ix(KaminoVaultRegistry.PROGRAM_ID, justUnder), memo)),
                vault,
                KaminoAction.WITHDRAW,
            )
        }
    }

    @Test
    fun `a short kVault instruction carrying no amount is not mistaken for the sentinel`() {
        assertDoesNotThrow {
            KaminoTransactionValidator.validate(
                decoded(listOf(ix(KaminoVaultRegistry.PROGRAM_ID, byteArrayOf(1, 2, 3)), memo)),
                vault,
                KaminoAction.DEPOSIT,
            )
        }
    }

    @Test
    fun `instruction value semantics do not depend on array identity`() {
        // Guards the hand-written equals: without it, structurally identical instructions would
        // compare unequal and the memo checks would silently stop matching.
        assertEquals(
            ix(KaminoAttributionMemo.MEMO_PROGRAM_ID, byteArrayOf(0x38, 0x6b, 0x32, 0x6d, 0x7a)),
            ix(KaminoAttributionMemo.MEMO_PROGRAM_ID, byteArrayOf(0x38, 0x6b, 0x32, 0x6d, 0x7a)),
        )
    }

    private fun tokenIx(discriminator: Int, accounts: List<String>) =
        KaminoTxInstruction(
            programId = TOKEN_PROGRAM,
            data = byteArrayOf(discriminator.toByte()),
            accounts = accounts,
        )

    private fun systemIx(discriminator: Int, accounts: List<String>) =
        KaminoTxInstruction(
            programId = SYSTEM_PROGRAM,
            data = byteArrayOf(discriminator.toByte(), 0, 0, 0, 1, 2, 3, 4),
            accounts = accounts,
        )

    /** [instruction] riding along on an otherwise valid wrapped-SOL vault transaction. */
    private fun validateOnSolVault(
        instruction: KaminoTxInstruction,
        wrappedSolAccount: String? = WRAPPED_SOL_ACCOUNT,
    ) =
        KaminoTransactionValidator.validate(
            decoded(listOf(ix(KaminoVaultRegistry.PROGRAM_ID, byteArrayOf(1)), instruction, memo)),
            KaminoVaultRegistry.ALLEZ_SOL,
            KaminoAction.DEPOSIT,
            signerAddress = DEFAULT_FEE_PAYER,
            wrappedSolAccount = wrappedSolAccount,
        )

    private fun solVaultRejection(
        instruction: KaminoTxInstruction,
        wrappedSolAccount: String? = WRAPPED_SOL_ACCOUNT,
    ): String =
        assertThrows<KaminoTransactionRejected> {
                validateOnSolVault(instruction, wrappedSolAccount)
            }
            .message
            .orEmpty()

    @Test
    fun `every System instruction other than the wrap is refused`() {
        // What a deny-list of transfers cannot reach, all of it authorised by the fee-payer
        // signature the message already carries: `Assign` (1) hands the signer's own system account
        // to a program the response chose, `CreateAccountWithSeed` (3) funds an account it chose
        // with an amount it chose, `AssignWithSeed` (10) does the first through a derived address.
        // None of them is a transfer.
        for (discriminator in listOf(0, 1, 3, 8, 10, 11)) {
            val reason =
                solVaultRejection(
                    systemIx(discriminator, listOf(DEFAULT_FEE_PAYER, WRAPPED_SOL_ACCOUNT))
                )
            assertTrue(reason.contains("System instruction $discriminator"), reason)
        }
    }

    @Test
    fun `every SPL token instruction other than the wrapped-SOL bookkeeping is refused`() {
        // Likewise on the token side: `Approve` (4) and `SetAuthority` (6) hand the account over
        // instead of draining it, so the drain arrives later and unsigned by this wallet, and
        // `Burn` (8) destroys the balance where it stands.
        for (discriminator in listOf(3, 4, 6, 7, 8, 12)) {
            val reason = solVaultRejection(tokenIx(discriminator, listOf(WRAPPED_SOL_ACCOUNT)))
            assertTrue(reason.contains("SPL token instruction $discriminator"), reason)
        }
    }

    @Test
    fun `the wrap's sync and the unwrap are allowed on the wrapped-SOL vault`() {
        // The other half of an allow-list, and the half a deny-list never had to get right: these
        // two are what a live Allez SOL deposit and withdraw actually carry, so refusing them would
        // block every real SOL position rather than let an extra instruction through.
        assertDoesNotThrow { validateOnSolVault(tokenIx(17, listOf(WRAPPED_SOL_ACCOUNT))) }
        assertDoesNotThrow {
            validateOnSolVault(
                tokenIx(9, listOf(WRAPPED_SOL_ACCOUNT, DEFAULT_FEE_PAYER, DEFAULT_FEE_PAYER))
            )
        }
    }

    @Test
    fun `an unwrap paid out to another account is refused`() {
        // Closing the wrapped-SOL account pays its whole balance out, which on a full exit is the
        // entire withdrawn amount. Rewriting this one address redirects the lot while every other
        // instruction still reads as the withdraw the user asked for.
        val reason =
            solVaultRejection(
                tokenIx(9, listOf(WRAPPED_SOL_ACCOUNT, OTHER_ACCOUNT, DEFAULT_FEE_PAYER))
            )
        assertTrue(reason.contains("rather than to the wallet"), reason)
    }

    @Test
    fun `an unwrap authorised by another account is refused`() {
        val reason =
            solVaultRejection(
                tokenIx(9, listOf(WRAPPED_SOL_ACCOUNT, DEFAULT_FEE_PAYER, OTHER_ACCOUNT))
            )
        assertTrue(reason.contains("under the authority of"), reason)
    }

    @Test
    fun `a wrap credited to another account is refused`() {
        val reason = solVaultRejection(tokenIx(17, listOf(OTHER_ACCOUNT)))
        assertTrue(reason.contains("credits $OTHER_ACCOUNT"), reason)
    }

    @Test
    fun `an unwrap of an account this app has never seen is allowed once the wallet is paid`() {
        // Deliberately looser than the wrap: only the unstaked withdraw could be captured, and a
        // staked exit may close accounts no fixture shows, so the close is pinned by recipient
        // rather than by subject. Nothing is conceded — the wallet can only close accounts it owns,
        // and the lamports land on the wallet either way. Pinning the subject instead would refuse
        // a real staked withdraw to buy no extra protection.
        assertDoesNotThrow {
            validateOnSolVault(
                tokenIx(9, listOf(OTHER_ACCOUNT, DEFAULT_FEE_PAYER, DEFAULT_FEE_PAYER))
            )
        }
    }

    @Test
    fun `an account hidden behind a lookup table is refused rather than skipped`() {
        // A versioned message may load accounts from a lookup table, and the decoder cannot resolve
        // those without fetching the tables, so it names them unresolved. A response that puts the
        // recipient behind its own table must therefore fail the comparison — a check that treats
        // "cannot tell" as "not a mismatch" is the same hole in a different place.
        val reason =
            solVaultRejection(
                tokenIx(
                    9,
                    listOf(
                        WRAPPED_SOL_ACCOUNT,
                        KaminoTransactionDecoder.UNKNOWN_ACCOUNT,
                        DEFAULT_FEE_PAYER,
                    ),
                )
            )
        assertTrue(reason.contains("rather than to the wallet"), reason)
    }

    @Test
    fun `crediting a wrap is refused on a plain-token vault`() {
        // Neither captured USDC shape carries a top-level token instruction at all — that vault
        // moves its tokens through the kVault program's own CPI — so a wrap is allowed only where
        // there is something to wrap.
        val reason =
            rejection(
                listOf(
                    ix(KaminoVaultRegistry.PROGRAM_ID),
                    tokenIx(17, listOf(WRAPPED_SOL_ACCOUNT)),
                    memo,
                )
            )
        assertTrue(reason.contains("only the wrapped-SOL vault"), reason)
    }

    @Test
    fun `crediting a wrap is refused when the wrapped-SOL account could not be derived`() {
        // Same posture the transfer already took: with nothing to compare against, refuse rather
        // than wave through.
        val reason =
            solVaultRejection(tokenIx(17, listOf(WRAPPED_SOL_ACCOUNT)), wrappedSolAccount = null)
        assertTrue(reason.contains("could not be derived"), reason)
    }

    private companion object {
        const val DEFAULT_FEE_PAYER = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"
        const val OTHER_ACCOUNT = "SomeoneElsesAccount11111111111111111111111"
        const val SYSTEM_PROGRAM = "11111111111111111111111111111111"
        const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

        /** [DEFAULT_FEE_PAYER]'s wrapped-SOL account, as the Allez SOL fixtures derive it. */
        const val WRAPPED_SOL_ACCOUNT = "GppmkdEmuqNgS7uY5SSN3gXEamJrcPG9197wBdQ37NLc"
    }
}
