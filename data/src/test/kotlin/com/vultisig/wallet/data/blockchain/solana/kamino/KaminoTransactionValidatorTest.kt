package com.vultisig.wallet.data.blockchain.solana.kamino

import java.math.BigInteger
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

    /** The shape a prepared deposit actually has: compute budget, ATA, kVault, two farms, memo. */
    private fun depositInstructions(
        memoInstruction: KaminoTxInstruction? = memo,
        budget: List<KaminoTxInstruction> = budget(),
    ) =
        budget +
            listOfNotNull(
                ix("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"),
                ix(KaminoVaultRegistry.PROGRAM_ID, byteArrayOf(1, 2, 3)),
                ix(KaminoVaultRegistry.FARMS_PROGRAM_ID),
                ix(KaminoVaultRegistry.FARMS_PROGRAM_ID),
                memoInstruction,
            )

    private fun budget(
        forVault: KaminoVault = vault,
        action: KaminoAction = KaminoAction.DEPOSIT,
        price: BigInteger = KaminoComputeBudget.FALLBACK_UNIT_PRICE,
        limit: BigInteger = KaminoComputeBudget.unitLimitFor(forVault, action),
    ) = computeBudgetInstructions(forVault, action, price, limit)

    /**
     * Most rules do not care who pays, so the fee payer defaults to a benign value; the fee-payer
     * test sets it explicitly.
     */
    private fun decoded(
        instructions: List<KaminoTxInstruction>,
        feePayer: String? = DEFAULT_FEE_PAYER,
    ) =
        KaminoDecodedTransaction(
            feePayer = feePayer,
            instructions = instructions,
            requiredSignatures = 1,
            isUnsigned = true,
        )

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
                    budget(action = KaminoAction.WITHDRAW) +
                        listOf(ix(KaminoVaultRegistry.PROGRAM_ID, byteArrayOf(9)), memo)
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
                    ix("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", byteArrayOf(3, 0, 0, 0)),
                    memo,
                )
            )
        assertTrue(reason.contains("top-level SPL token transfer"), reason)
    }

    @Test
    fun `TransferChecked is refused too, not just the bare transfer`() {
        val reason =
            rejection(
                listOf(
                    ix(KaminoVaultRegistry.PROGRAM_ID),
                    // `TransferChecked` is discriminator 12.
                    ix("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", byteArrayOf(12, 0)),
                    memo,
                )
            )
        assertTrue(reason.contains("top-level SPL token transfer"), reason)
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
        val instructions =
            budget(forVault = KaminoVaultRegistry.ALLEZ_SOL) + listOf(kvault, systemTransfer, memo)

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
                    budget() +
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
                decoded(
                    budget(action = KaminoAction.WITHDRAW) +
                        listOf(ix(KaminoVaultRegistry.PROGRAM_ID, justUnder), memo)
                ),
                vault,
                KaminoAction.WITHDRAW,
            )
        }
    }

    @Test
    fun `a short kVault instruction carrying no amount is not mistaken for the sentinel`() {
        assertDoesNotThrow {
            KaminoTransactionValidator.validate(
                decoded(
                    budget() +
                        listOf(ix(KaminoVaultRegistry.PROGRAM_ID, byteArrayOf(1, 2, 3)), memo)
                ),
                vault,
                KaminoAction.DEPOSIT,
            )
        }
    }

    @Test
    fun `a transaction carrying no compute budget is refused`() {
        // Not a fee question alone: these consume 250,000-310,000 units against a 200,000 default,
        // so a budget-less Kamino transaction aborts on chain after a full signing ceremony.
        val reason = rejection(depositInstructions(budget = emptyList()))

        assertTrue(reason.contains("no compute budget"), reason)
    }

    @Test
    fun `a compute-unit price above the ceiling is refused`() {
        // The price is a u64 multiplied by a six-figure limit, so unbounded here is an unbounded
        // fee — while the row that displays it clamps at MAX_UNIT_PRICE and would show a hundredth
        // of what the runtime charges. It is also the range iOS's own decoder accepts.
        val reason =
            rejection(
                depositInstructions(
                    budget = budget(price = KaminoComputeBudget.MAX_UNIT_PRICE.multiply(TEN))
                )
            )

        assertTrue(reason.contains("outside"), reason)
    }

    @Test
    fun `a compute-unit limit this app would not reserve is refused`() {
        // The fee row multiplies the local limit by the relayed price, so bytes reserving some
        // other limit are charged for work the quoted figure never priced.
        val reason =
            rejection(depositInstructions(budget = budget(limit = BigInteger.valueOf(1_400_000))))

        assertTrue(reason.contains("compute-unit limit"), reason)
    }

    @Test
    fun `a deposit carrying the withdraw limit is refused`() {
        // The limit is per action, so the pair is checked against the action in hand rather than
        // against "some limit this app uses somewhere".
        val reason = rejection(depositInstructions(budget = budget(action = KaminoAction.WITHDRAW)))

        assertTrue(reason.contains("compute-unit limit"), reason)
    }

    @Test
    fun `a compute budget behind the instructions Kamino built is refused`() {
        // The leading pair is a cross-platform contract: iOS emits them at 0 and 1 and reads the
        // rest positionally, so a budget appended at the end is a transaction it will not join.
        val misplaced = depositInstructions(budget = emptyList()).dropLast(1) + budget() + memo

        val reason = rejection(misplaced)
        assertTrue(reason.contains("leading two instructions"), reason)
    }

    @Test
    fun `a duplicated compute budget is refused rather than read from its first entry`() {
        val reason = rejection(depositInstructions(budget = budget() + budget()))

        assertTrue(reason.contains("cannot read"), reason)
    }

    @Test
    fun `a transaction needing a second signature is refused`() {
        // The signing path fills slot 0 and leaves the rest as it found them, so this would be
        // broadcast a signature short — or completed by whoever supplied the bytes.
        val reason =
            assertThrows<KaminoTransactionRejected> {
                    KaminoTransactionValidator.validate(
                        decoded(depositInstructions()).copy(requiredSignatures = 2),
                        vault,
                        KaminoAction.DEPOSIT,
                    )
                }
                .message
                .orEmpty()

        assertTrue(reason.contains("required signatures"), reason)
    }

    @Test
    fun `a transaction whose signature slot is already filled is refused`() {
        val reason =
            assertThrows<KaminoTransactionRejected> {
                    KaminoTransactionValidator.validate(
                        decoded(depositInstructions()).copy(isUnsigned = false),
                        vault,
                        KaminoAction.DEPOSIT,
                    )
                }
                .message
                .orEmpty()

        assertTrue(reason.contains("already carries a signature"), reason)
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

    private companion object {
        const val DEFAULT_FEE_PAYER = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"
        val TEN: BigInteger = BigInteger.valueOf(10)
        const val OTHER_ACCOUNT = "SomeoneElsesAccount11111111111111111111111"
    }
}
