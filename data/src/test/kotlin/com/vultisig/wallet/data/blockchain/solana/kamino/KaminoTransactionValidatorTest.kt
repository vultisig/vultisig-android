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

    /**
     * A kVault instruction shaped like the captured ones: the wallet as authority, its own token
     * and share accounts in the slots the IDL fixes, and the amount that was asked for.
     *
     * The filler in the other slots stands in for the accounts a real transaction loads from the
     * vault's address lookup table, which decode to [KaminoTransactionDecoder.UNKNOWN_ACCOUNT].
     */
    private fun kvault(
        action: KaminoAction = KaminoAction.DEPOSIT,
        discriminator: String = discriminatorFor(action),
        amount: BigInteger = AMOUNT,
        user: String = DEFAULT_FEE_PAYER,
        tokenAccount: String = TOKEN_ACCOUNT,
        shareAccount: String = SHARE_ACCOUNT,
    ): KaminoTxInstruction {
        val accounts = MutableList(9) { KaminoTransactionDecoder.UNKNOWN_ACCOUNT }
        accounts[0] = user
        accounts[if (action == KaminoAction.DEPOSIT) 6 else 5] = tokenAccount
        accounts[7] = shareAccount
        return KaminoTxInstruction(
            programId = KaminoVaultRegistry.PROGRAM_ID,
            data = bytes(discriminator) + littleEndian(amount, bytes = 8),
            accounts = accounts,
        )
    }

    private fun discriminatorFor(action: KaminoAction) =
        when (action) {
            KaminoAction.DEPOSIT -> KVAULT_DEPOSIT
            KaminoAction.WITHDRAW -> KVAULT_WITHDRAW_FROM_AVAILABLE
        }

    /**
     * A farms instruction with the wallet as authority and its share account in both slots the
     * checked instructions read one of — slot 4 for the stake, slot 3 for the unstaked withdrawal.
     */
    private fun farms(
        discriminator: String,
        argument: ByteArray = ByteArray(0),
        owner: String = DEFAULT_FEE_PAYER,
        shareAccount: String = SHARE_ACCOUNT,
    ): KaminoTxInstruction {
        val accounts = MutableList(6) { KaminoTransactionDecoder.UNKNOWN_ACCOUNT }
        accounts[0] = owner
        accounts[3] = shareAccount
        accounts[4] = shareAccount
        return KaminoTxInstruction(
            programId = KaminoVaultRegistry.FARMS_PROGRAM_ID,
            data = bytes(discriminator) + argument,
            accounts = accounts,
        )
    }

    /** `CreateIdempotent` for one of the wallet's own accounts, as every captured shape opens. */
    private fun createAta(account: String = SHARE_ACCOUNT, discriminator: Int = 1) =
        KaminoTxInstruction(
            programId = ASSOCIATED_TOKEN_PROGRAM,
            data = byteArrayOf(discriminator.toByte()),
            accounts = listOf(DEFAULT_FEE_PAYER, account, DEFAULT_FEE_PAYER),
        )

    /** The shape a prepared deposit actually has: ATA, kVault, two farms, compute budget, memo. */
    private fun depositInstructions(memoInstruction: KaminoTxInstruction? = memo) =
        listOfNotNull(
            createAta(),
            kvault(),
            farms(FARMS_INITIALIZE_USER),
            farms(FARMS_STAKE, argument = littleEndian(U64_MAX, bytes = 8)),
            ix("ComputeBudget111111111111111111111111111111"),
            memoInstruction,
        )

    /**
     * Everything the preparer derives locally before it validates, defaulted so each test overrides
     * only what it is about. The fee payer defaults to the signer, since most rules do not care who
     * pays and the fee-payer test sets it explicitly.
     */
    private fun validate(
        instructions: List<KaminoTxInstruction>,
        vault: KaminoVault = this.vault,
        action: KaminoAction = KaminoAction.DEPOSIT,
        feePayer: String? = DEFAULT_FEE_PAYER,
        signerAddress: String? = DEFAULT_FEE_PAYER,
        tokenAccount: String? = TOKEN_ACCOUNT,
        shareAccount: String? = SHARE_ACCOUNT,
        amountBaseUnits: BigInteger? = AMOUNT,
    ) =
        KaminoTransactionValidator.validate(
            decoded = KaminoDecodedTransaction(feePayer = feePayer, instructions = instructions),
            vault = vault,
            action = action,
            signerAddress = signerAddress,
            tokenAccount = tokenAccount,
            shareAccount = shareAccount,
            amountBaseUnits = amountBaseUnits,
        )

    private fun rejection(
        instructions: List<KaminoTxInstruction>,
        vault: KaminoVault = this.vault,
        action: KaminoAction = KaminoAction.DEPOSIT,
        feePayer: String? = DEFAULT_FEE_PAYER,
        signerAddress: String? = DEFAULT_FEE_PAYER,
        tokenAccount: String? = TOKEN_ACCOUNT,
        shareAccount: String? = SHARE_ACCOUNT,
        amountBaseUnits: BigInteger? = AMOUNT,
    ): String =
        assertThrows<KaminoTransactionRejected> {
                validate(
                    instructions = instructions,
                    vault = vault,
                    action = action,
                    feePayer = feePayer,
                    signerAddress = signerAddress,
                    tokenAccount = tokenAccount,
                    shareAccount = shareAccount,
                    amountBaseUnits = amountBaseUnits,
                )
            }
            .message
            .orEmpty()

    @Test
    fun `a well-formed deposit passes`() {
        assertDoesNotThrow { validate(depositInstructions()) }
    }

    @Test
    fun `a withdraw without the farms instructions passes`() {
        assertDoesNotThrow {
            validate(
                listOf(
                    kvault(action = KaminoAction.WITHDRAW),
                    ix("ComputeBudget111111111111111111111111111111"),
                    memo,
                ),
                action = KaminoAction.WITHDRAW,
            )
        }
    }

    @Test
    fun `both withdraw shapes are accepted, because which one arrives is the vault's business`() {
        // `withdraw` and `withdraw_from_available` are the same withdraw; which the builder emits
        // says only whether the vault's liquid buffer covered the request, and both are in live
        // use. They share an account layout by IDL construction, so one check serves both.
        listOf(KVAULT_WITHDRAW, KVAULT_WITHDRAW_FROM_AVAILABLE).forEach { discriminator ->
            assertDoesNotThrow {
                validate(
                    listOf(
                        kvault(action = KaminoAction.WITHDRAW, discriminator = discriminator),
                        memo,
                    ),
                    action = KaminoAction.WITHDRAW,
                )
            }
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
    fun `a second kVault instruction is refused rather than checked twice`() {
        // Every amount below is compared instruction by instruction, so two instructions each
        // carrying the figure on screen would both pass and move the money twice.
        val reason = rejection(listOf(kvault(), kvault(), memo))
        assertTrue(reason.contains("exactly one kVault instruction"), reason)
    }

    @Test
    fun `a second System instruction is refused rather than wrapping twice`() {
        val wrap = systemIx(2, listOf(DEFAULT_FEE_PAYER, WRAPPED_SOL_ACCOUNT))
        val reason =
            rejection(
                listOf(solVaultKvault(), wrap, wrap, memo),
                vault = KaminoVaultRegistry.ALLEZ_SOL,
                tokenAccount = WRAPPED_SOL_ACCOUNT,
                shareAccount = SOL_SHARE_ACCOUNT,
            )
        assertTrue(reason.contains("at most one System instruction"), reason)
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
        // not that, and the app's own injection names none — so an account list means it came from
        // somewhere else, even when the bytes happen to be right.
        val reason =
            rejection(
                listOf(
                    kvault(),
                    KaminoTxInstruction(
                        programId = KaminoAttributionMemo.MEMO_PROGRAM_ID,
                        data = KaminoAttributionMemo.TAG.toByteArray(),
                        accounts = listOf(DEFAULT_FEE_PAYER),
                    ),
                )
            )
        assertTrue(reason.contains("name no accounts"), reason)
    }

    @Test
    fun `a memo that is not last is refused`() {
        val reason =
            rejection(listOf(kvault(), memo, ix("ComputeBudget111111111111111111111111111111")))
        assertTrue(reason.contains("final instruction"), reason)
    }

    @Test
    fun `an empty transaction is refused`() {
        assertTrue(rejection(emptyList()).contains("no instructions"))
    }

    @Test
    fun `a vault outside the registry is refused before anything else is checked`() {
        val uncurated = vault.copy(address = "2Z6C84pCc2ri8t39jvRCXnTGFQqUJf1mMpUMtpeFfhyB")
        val reason = rejection(depositInstructions(), vault = uncurated)
        assertTrue(reason.contains("not a vault this app transacts with"), reason)
    }

    @Test
    fun `a smuggled SPL token transfer is refused even though the transaction is otherwise valid`() {
        // The hole that program allow-listing alone leaves: a deposit legitimately needs the Token
        // program, so an added transfer would pass a check that only asks which programs run.
        val reason =
            rejection(
                listOf(
                    kvault(),
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
                    kvault(),
                    // `TransferChecked` is discriminator 12.
                    ix(TOKEN_PROGRAM, byteArrayOf(12, 0)),
                    memo,
                )
            )
        assertTrue(reason.contains("SPL token instruction 12"), reason)
    }

    @Test
    fun `moving lamports is refused for a token vault and allowed for the wrapped-SOL one`() {
        // System `Transfer` is 2, little-endian over four bytes, followed by the lamport `u64`.
        val transfer = wrapOf(SOL_AMOUNT)

        val reason = rejection(listOf(kvault(), transfer, memo))
        assertTrue(reason.contains("moves lamports"), reason)

        // The SOL vault has to wrap, so the same instruction is expected there.
        assertDoesNotThrow {
            validate(
                listOf(solVaultKvault(), transfer, memo),
                vault = KaminoVaultRegistry.ALLEZ_SOL,
                tokenAccount = WRAPPED_SOL_ACCOUNT,
                shareAccount = SOL_SHARE_ACCOUNT,
                amountBaseUnits = SOL_AMOUNT,
            )
        }
    }

    @Test
    fun `a wrap of more lamports than the deposit is refused`() {
        // The instruction #5603's third finding is about: reading only the four discriminator bytes
        // leaves the lamports unbound, so a response could wrap the whole SOL balance while the
        // screen shows the typed amount. The deposit then consumes what was asked for and the rest
        // sits in a wrapped-SOL account this app has no screen for.
        val reason =
            rejection(
                listOf(solVaultKvault(), wrapOf(SOL_AMOUNT.multiply(BigInteger.TEN)), memo),
                vault = KaminoVaultRegistry.ALLEZ_SOL,
                tokenAccount = WRAPPED_SOL_ACCOUNT,
                shareAccount = SOL_SHARE_ACCOUNT,
                amountBaseUnits = SOL_AMOUNT,
            )
        assertTrue(reason.contains("rather than the $SOL_AMOUNT"), reason)
    }

    @Test
    fun `a wrap is refused when the amount that was asked for could not be read`() {
        // The wrap leads, as it does on the wire: the deposit cannot spend wrapped SOL that has not
        // been wrapped yet.
        val reason =
            rejection(
                listOf(wrapOf(SOL_AMOUNT), solVaultKvault(), memo),
                vault = KaminoVaultRegistry.ALLEZ_SOL,
                tokenAccount = WRAPPED_SOL_ACCOUNT,
                shareAccount = SOL_SHARE_ACCOUNT,
                amountBaseUnits = null,
            )
        assertTrue(reason.contains("could not be read"), reason)
    }

    @Test
    fun `a wrap carrying no lamport amount at all is refused`() {
        // Four discriminator bytes and nothing after them: the shape the old check read, and the
        // one it accepted.
        val truncated =
            KaminoTxInstruction(
                programId = SYSTEM_PROGRAM,
                data = byteArrayOf(2, 0, 0, 0),
                accounts = listOf(DEFAULT_FEE_PAYER, WRAPPED_SOL_ACCOUNT),
            )
        val reason =
            rejection(
                listOf(solVaultKvault(), truncated, memo),
                vault = KaminoVaultRegistry.ALLEZ_SOL,
                tokenAccount = WRAPPED_SOL_ACCOUNT,
                shareAccount = SOL_SHARE_ACCOUNT,
                amountBaseUnits = SOL_AMOUNT,
            )
        assertTrue(reason.contains("no lamport amount"), reason)
    }

    @Test
    fun `a wrap is refused on a withdraw, which has nothing to wrap`() {
        val reason =
            rejection(
                listOf(solVaultKvault(action = KaminoAction.WITHDRAW), wrapOf(SOL_AMOUNT), memo),
                vault = KaminoVaultRegistry.ALLEZ_SOL,
                action = KaminoAction.WITHDRAW,
                tokenAccount = WRAPPED_SOL_ACCOUNT,
                shareAccount = SOL_SHARE_ACCOUNT,
                amountBaseUnits = SOL_AMOUNT,
            )
        assertTrue(reason.contains("no Kamino withdraw performs"), reason)
    }

    @Test
    fun `a transaction authorised by someone other than the wallet is refused`() {
        val reason = rejection(listOf(kvault(), memo), feePayer = OTHER_ACCOUNT)
        assertTrue(reason.contains("authorised by"), reason)

        // The wallet's own transaction passes.
        assertDoesNotThrow { validate(listOf(kvault(), memo)) }
    }

    @Test
    fun `a lamport transfer to an address the deposit does not reference is refused`() {
        // Allowed on the wrapped-SOL vault, but not to anywhere: the only lamports a deposit moves
        // go into the wSOL account it then deposits from. A transfer somewhere else is not a wrap.
        val strayTransfer =
            KaminoTxInstruction(
                programId = SYSTEM_PROGRAM,
                data = byteArrayOf(2, 0, 0, 0) + littleEndian(SOL_AMOUNT, bytes = 8),
                accounts = listOf(DEFAULT_FEE_PAYER, OTHER_ACCOUNT),
            )

        val reason =
            rejection(
                listOf(solVaultKvault(), strayTransfer, memo),
                vault = KaminoVaultRegistry.ALLEZ_SOL,
                tokenAccount = WRAPPED_SOL_ACCOUNT,
                shareAccount = SOL_SHARE_ACCOUNT,
                amountBaseUnits = SOL_AMOUNT,
            )
        assertTrue(reason.contains("rather than to the wallet"), reason)
    }

    @Test
    fun `a transaction paid for by another account is refused on the message, not an instruction`() {
        // The earlier check read the first account of the first instruction, which says nothing
        // about who authorises the transaction: this fixture names the wallet there while the
        // message is paid for by someone else.
        val reason = rejection(listOf(kvault(), memo), feePayer = OTHER_ACCOUNT)
        assertTrue(reason.contains("authorised by"), reason)
    }

    @Test
    fun `a wrap is refused outright when the expected account could not be derived`() {
        // Unverifiable is not the same as fine: with no derived address there is nothing to compare
        // the destination against.
        val reason =
            rejection(
                listOf(wrapOf(SOL_AMOUNT), solVaultKvault(), memo),
                vault = KaminoVaultRegistry.ALLEZ_SOL,
                tokenAccount = null,
                shareAccount = SOL_SHARE_ACCOUNT,
                amountBaseUnits = SOL_AMOUNT,
            )
        assertTrue(reason.contains("could not be derived"), reason)
    }

    @Test
    fun `the withdraw-everything sentinel is refused on any device`() {
        // u64::MAX is what the kVault program reads as "withdraw everything". This app never
        // produces one — the form's maximum sits strictly below the balance so the API cannot
        // answer with it, and an over-request is refused rather than clamped — so a transaction
        // carrying it did not come from here. It is also the one amount whose consequence is the
        // whole position rather than the figure on screen, which is why it is refused rather than
        // merely disclosed, and why it is named ahead of the plain amount mismatch it also is.
        val reason =
            rejection(
                listOf(kvault(action = KaminoAction.WITHDRAW, amount = U64_MAX), memo),
                action = KaminoAction.WITHDRAW,
            )
        assertTrue(reason.contains("withdraw-everything sentinel"), reason)
    }

    @Test
    fun `an ordinary amount one below the sentinel is allowed when it is what was asked for`() {
        // The guard must key on the sentinel exactly, not on "a large amount".
        val justUnder = U64_MAX.subtract(BigInteger.ONE)
        assertDoesNotThrow {
            validate(
                listOf(kvault(action = KaminoAction.WITHDRAW, amount = justUnder), memo),
                action = KaminoAction.WITHDRAW,
                amountBaseUnits = justUnder,
            )
        }
    }

    @Test
    fun `a kVault instruction moving another figure than the one on screen is refused`() {
        val reason = rejection(listOf(kvault(amount = AMOUNT.multiply(BigInteger.TEN)), memo))
        assertTrue(reason.contains("rather than the $AMOUNT"), reason)
    }

    @Test
    fun `a short kVault instruction carrying no amount is refused rather than skipped`() {
        val truncated =
            KaminoTxInstruction(
                programId = KaminoVaultRegistry.PROGRAM_ID,
                data = bytes(KVAULT_DEPOSIT),
                accounts = kvault().accounts,
            )
        val reason = rejection(listOf(truncated, memo))
        assertTrue(reason.contains("no amount to check"), reason)
    }

    @Test
    fun `a kVault instruction that is not the action the app asked for is refused`() {
        val reason = rejection(listOf(kvault(discriminator = KVAULT_WITHDRAW_FROM_AVAILABLE), memo))
        assertTrue(reason.contains("not the kVault deposit"), reason)

        val theOtherWay =
            rejection(
                listOf(
                    kvault(action = KaminoAction.WITHDRAW, discriminator = KVAULT_DEPOSIT),
                    memo,
                ),
                action = KaminoAction.WITHDRAW,
            )
        assertTrue(theOtherWay.contains("not the kVault withdraw"), theOtherWay)
    }

    @Test
    fun `a kVault instruction naming another share account is refused`() {
        // The finding this pins: nothing else in the transaction binds the instruction to the vault
        // on screen. The vault account travels in an address lookup table this decode does not
        // resolve, so a substituted `vault_state` — including one the response made itself through
        // the permissionless `init_vault` — would deposit the balance into a vault the app never
        // curated while the screen showed the registry address. The share account is derived
        // locally from the registry's share mint and this signer, so it cannot be substituted.
        val reason = rejection(listOf(kvault(shareAccount = OTHER_ACCOUNT), memo))
        assertTrue(reason.contains("share account for this vault"), reason)
    }

    @Test
    fun `a kVault instruction hiding its share account behind a lookup table is refused`() {
        // "Cannot tell" is not "not a mismatch": an account the response loads from its own table
        // decodes as unresolved and must fail the comparison rather than skip it.
        val reason =
            rejection(listOf(kvault(shareAccount = KaminoTransactionDecoder.UNKNOWN_ACCOUNT), memo))
        assertTrue(reason.contains("share account for this vault"), reason)
    }

    @Test
    fun `a kVault instruction paying out to another token account is refused`() {
        // Slot 5 of a withdraw is where the tokens land. Rewriting it hands the payout to whoever
        // the response names while every other instruction still reads as the withdraw asked for.
        val reason =
            rejection(
                listOf(kvault(action = KaminoAction.WITHDRAW, tokenAccount = OTHER_ACCOUNT), memo),
                action = KaminoAction.WITHDRAW,
            )
        assertTrue(reason.contains("token account for this vault"), reason)
    }

    @Test
    fun `a kVault instruction authorised by another account is refused`() {
        val reason = rejection(listOf(kvault(user = OTHER_ACCOUNT), memo))
        assertTrue(reason.contains("the authority this vault move runs under"), reason)
    }

    @Test
    fun `a kVault instruction is refused when the wallet's own accounts could not be derived`() {
        val noShares = rejection(listOf(kvault(), memo), shareAccount = null)
        assertTrue(noShares.contains("could not be derived locally"), noShares)

        val noTokens = rejection(listOf(kvault(), memo), tokenAccount = null)
        assertTrue(noTokens.contains("could not be derived locally"), noTokens)

        val noAmount = rejection(listOf(kvault(), memo), amountBaseUnits = null)
        assertTrue(noAmount.contains("could not be read"), noAmount)
    }

    @Test
    fun `a kVault instruction naming too few accounts is refused`() {
        val short =
            KaminoTxInstruction(
                programId = KaminoVaultRegistry.PROGRAM_ID,
                data = bytes(KVAULT_DEPOSIT) + littleEndian(AMOUNT, bytes = 8),
                accounts = listOf(DEFAULT_FEE_PAYER),
            )
        val reason = rejection(listOf(short, memo))
        assertTrue(reason.contains("too few accounts"), reason)
    }

    @Test
    fun `a farms instruction the app never performs is refused`() {
        // The gap an allow-list of programs alone leaves on the farms side. Every launch vault has
        // a farm, so the program is allow-listed and its instructions went unread — and
        // `transfer_ownership` moves the whole staked position to an account that never signs,
        // authorised by the signature the message already carries. Nothing about it looks like a
        // transfer. The discriminator is the Anchor one: sha256("global:transfer_ownership")[0..8].
        val reason =
            rejection(depositInstructions().dropLast(1) + farms(FARMS_TRANSFER_OWNERSHIP) + memo)
        assertTrue(reason.contains("farms instruction no Kamino deposit or withdraw"), reason)
    }

    @Test
    fun `the farms instructions a real deposit and withdraw carry are allowed`() {
        // The other half of an allow-list, and the half a deny-list never had to get right: these
        // four are what the captured deposit carries and what a staked withdraw adds, so refusing
        // any of them would block a real position rather than let an extra instruction through.
        assertDoesNotThrow { validate(depositInstructions()) }

        assertDoesNotThrow {
            validate(
                listOf(
                    farms(
                        FARMS_UNSTAKE,
                        argument = littleEndian(unstakeScaled(AMOUNT), bytes = 16),
                    ),
                    farms(FARMS_WITHDRAW_UNSTAKED_DEPOSITS),
                    kvault(action = KaminoAction.WITHDRAW),
                    memo,
                ),
                action = KaminoAction.WITHDRAW,
            )
        }
    }

    @Test
    fun `a second unstake is refused rather than releasing twice what the withdraw burns`() {
        // The bound each unstake is held to is per-instruction, so two of them each carrying the
        // requested figure both pass it. The withdraw then burns that figure once, and the shares
        // the second release took out of the farm stay out of it — earning nothing, and invisible
        // on a screen that shows one amount. Exactly the harm the per-instruction bound names, and
        // the only way to close it is to count them.
        val unstake =
            farms(FARMS_UNSTAKE, argument = littleEndian(unstakeScaled(AMOUNT), bytes = 16))
        val reason =
            rejection(
                listOf(
                    unstake,
                    unstake,
                    farms(FARMS_WITHDRAW_UNSTAKED_DEPOSITS),
                    kvault(action = KaminoAction.WITHDRAW),
                    memo,
                ),
                action = KaminoAction.WITHDRAW,
            )
        assertTrue(reason.contains("at most one farms unstake instruction, found 2"), reason)
    }

    @Test
    fun `each of the four farms instructions is bounded to one, not merely checked one at a time`() {
        // Every one of the four passes its own checks a second time, so each needs the count as
        // well: a repeated `initialize_user` funds another farm's user state out of the wallet's
        // rent, and a repeated stake or unstaked withdrawal moves shares a second time. iOS names
        // all four `repeatable: false` in `KaminoInstructionSequence.expected`.
        val bounded =
            mapOf(
                FARMS_INITIALIZE_USER to "initialize_user",
                FARMS_STAKE to "stake",
                FARMS_UNSTAKE to "unstake",
                FARMS_WITHDRAW_UNSTAKED_DEPOSITS to "withdraw_unstaked_deposits",
            )
        bounded.forEach { (discriminator, name) ->
            val duplicate = farms(discriminator)
            val reason = rejection(listOf(kvault(), duplicate, duplicate, memo))
            assertTrue(reason.contains("at most one farms $name instruction, found 2"), reason)
        }
    }

    @Test
    fun `a stake that moves shares through another account is refused`() {
        val reason =
            rejection(
                listOf(
                    kvault(),
                    farms(
                        FARMS_STAKE,
                        argument = littleEndian(U64_MAX, bytes = 8),
                        shareAccount = OTHER_ACCOUNT,
                    ),
                    memo,
                )
            )
        assertTrue(reason.contains("share account for this vault"), reason)
    }

    @Test
    fun `a stake of anything but the whole balance is refused`() {
        // Kamino stakes the entire share balance rather than the amount just minted, so the
        // argument is the sentinel every time. A different value is a behaviour change.
        val reason =
            rejection(
                listOf(
                    kvault(),
                    farms(FARMS_STAKE, argument = littleEndian(AMOUNT, bytes = 8)),
                    memo,
                )
            )
        assertTrue(reason.contains("stakes an amount this app has never seen"), reason)
    }

    @Test
    fun `an unstake releasing more shares than the withdraw burns is refused`() {
        // How many a withdraw legitimately releases is `requested − alreadyUnstaked`, and the
        // second term is a balance this app does not hold here. The bound needs neither: a withdraw
        // cannot take more out of the farm than it burns, and shares released beyond that sit
        // outside the farm earning nothing, invisible on a screen that shows an amount.
        val tooMuch = unstakeScaled(AMOUNT.add(BigInteger.ONE))
        val reason =
            rejection(
                listOf(
                    farms(FARMS_UNSTAKE, argument = littleEndian(tooMuch, bytes = 16)),
                    farms(FARMS_WITHDRAW_UNSTAKED_DEPOSITS),
                    kvault(action = KaminoAction.WITHDRAW),
                    memo,
                ),
                action = KaminoAction.WITHDRAW,
            )
        assertTrue(reason.contains("more shares from the farm"), reason)
    }

    @Test
    fun `an unstake read as a u64 would compare a number that means nothing`() {
        // `1 share × 10^18` is 0x0DE0B6B3A7640000; its low eight bytes are the whole value only
        // because the figure is small. A release of 100 shares is 16 bytes wide, and reading its
        // low half gives 0x6BC75E2D63100000 — a plausible-looking number that is not the one on the
        // wire. The check reads all sixteen, so the bound holds at any size.
        val hundredShares = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(6))
        assertDoesNotThrow {
            validate(
                listOf(
                    farms(
                        FARMS_UNSTAKE,
                        argument = littleEndian(unstakeScaled(hundredShares), bytes = 16),
                    ),
                    farms(FARMS_WITHDRAW_UNSTAKED_DEPOSITS),
                    kvault(action = KaminoAction.WITHDRAW, amount = hundredShares),
                    memo,
                ),
                action = KaminoAction.WITHDRAW,
                amountBaseUnits = hundredShares,
            )
        }
    }

    @Test
    fun `the unstaked share withdrawal must land in the wallet's own share account`() {
        // Where the released shares land, and the account the vault withdraw then burns from.
        val reason =
            rejection(
                listOf(
                    farms(
                        FARMS_UNSTAKE,
                        argument = littleEndian(unstakeScaled(AMOUNT), bytes = 16),
                    ),
                    farms(FARMS_WITHDRAW_UNSTAKED_DEPOSITS, shareAccount = OTHER_ACCOUNT),
                    kvault(action = KaminoAction.WITHDRAW),
                    memo,
                ),
                action = KaminoAction.WITHDRAW,
            )
        assertTrue(reason.contains("share account for this vault"), reason)
    }

    @Test
    fun `a farms instruction under another authority is refused`() {
        val reason =
            rejection(
                listOf(
                    kvault(),
                    farms(
                        FARMS_STAKE,
                        argument = littleEndian(U64_MAX, bytes = 8),
                        owner = OTHER_ACCOUNT,
                    ),
                    memo,
                )
            )
        assertTrue(reason.contains("farm authority"), reason)
    }

    @Test
    fun `farms instructions belonging to the other action are refused`() {
        // A deposit stakes and a withdraw unstakes; neither does both, in the template iOS matches
        // live transactions against. An unstake riding on a deposit would pull the position out of
        // the farm, where it stops earning and shows up on no screen.
        val stakeOnWithdraw =
            rejection(
                listOf(
                    kvault(action = KaminoAction.WITHDRAW),
                    farms(FARMS_STAKE, argument = littleEndian(U64_MAX, bytes = 8)),
                    memo,
                ),
                action = KaminoAction.WITHDRAW,
            )
        assertTrue(stakeOnWithdraw.contains("no Kamino withdraw performs"), stakeOnWithdraw)

        val unstakeOnDeposit =
            rejection(
                listOf(
                    kvault(),
                    farms(
                        FARMS_UNSTAKE,
                        argument = littleEndian(unstakeScaled(AMOUNT), bytes = 16),
                    ),
                    memo,
                )
            )
        assertTrue(unstakeOnDeposit.contains("no Kamino deposit performs"), unstakeOnDeposit)
    }

    @Test
    fun `an account created for anyone but the wallet is refused`() {
        // Creation is funded by the fee payer, so an account created for someone else is the wallet
        // paying rent into an address the response chose.
        val reason = rejection(listOf(createAta(OTHER_ACCOUNT), kvault(), memo))
        assertTrue(reason.contains("not one of the wallet's own accounts"), reason)
    }

    @Test
    fun `the wallet's own two accounts are both allowed to be created`() {
        assertDoesNotThrow {
            validate(listOf(createAta(SHARE_ACCOUNT), createAta(TOKEN_ACCOUNT), kvault(), memo))
        }
    }

    @Test
    fun `an associated-token instruction other than the idempotent create is refused`() {
        // `Create` (0) fails outright when the account exists, so a response emitting it would be
        // doing something other than making sure the account is there.
        val reason = rejection(listOf(createAta(discriminator = 0), kvault(), memo))
        assertTrue(reason.contains("associated-token instruction 0"), reason)
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

    private fun tokenIx(
        discriminator: Int,
        accounts: List<String>,
        programId: String = TOKEN_PROGRAM,
    ) =
        KaminoTxInstruction(
            programId = programId,
            data = byteArrayOf(discriminator.toByte()),
            accounts = accounts,
        )

    private fun systemIx(discriminator: Int, accounts: List<String>) =
        KaminoTxInstruction(
            programId = SYSTEM_PROGRAM,
            data =
                byteArrayOf(discriminator.toByte(), 0, 0, 0) + littleEndian(SOL_AMOUNT, bytes = 8),
            accounts = accounts,
        )

    /**
     * The wrap a real Allez SOL deposit carries: lamports out of the wallet, into its wSOL account.
     */
    private fun wrapOf(lamports: BigInteger) =
        KaminoTxInstruction(
            programId = SYSTEM_PROGRAM,
            data = byteArrayOf(2, 0, 0, 0) + littleEndian(lamports, bytes = 8),
            accounts = listOf(DEFAULT_FEE_PAYER, WRAPPED_SOL_ACCOUNT),
        )

    private fun solVaultKvault(action: KaminoAction = KaminoAction.DEPOSIT) =
        kvault(
            action = action,
            amount = SOL_AMOUNT,
            tokenAccount = WRAPPED_SOL_ACCOUNT,
            shareAccount = SOL_SHARE_ACCOUNT,
        )

    /** [instruction] riding along on an otherwise valid wrapped-SOL vault transaction. */
    private fun validateOnSolVault(
        instruction: KaminoTxInstruction,
        tokenAccount: String? = WRAPPED_SOL_ACCOUNT,
        action: KaminoAction = KaminoAction.DEPOSIT,
    ) =
        validate(
            listOf(instruction, solVaultKvault(action), memo),
            vault = KaminoVaultRegistry.ALLEZ_SOL,
            action = action,
            tokenAccount = tokenAccount,
            shareAccount = SOL_SHARE_ACCOUNT,
            amountBaseUnits = SOL_AMOUNT,
        )

    private fun solVaultRejection(
        instruction: KaminoTxInstruction,
        tokenAccount: String? = WRAPPED_SOL_ACCOUNT,
        action: KaminoAction = KaminoAction.DEPOSIT,
    ): String =
        assertThrows<KaminoTransactionRejected> {
                validateOnSolVault(instruction, tokenAccount, action)
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
    fun `a System instruction carrying no discriminator at all is refused`() {
        val reason =
            solVaultRejection(
                KaminoTxInstruction(
                    programId = SYSTEM_PROGRAM,
                    data = byteArrayOf(2, 0),
                    accounts = listOf(DEFAULT_FEE_PAYER, WRAPPED_SOL_ACCOUNT),
                )
            )
        assertTrue(reason.contains("no System instruction to check"), reason)
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
    fun `Token-2022 is held to the same allow-list as Token`() {
        // Both dispatch to the same check, so an instruction the Token program may not carry must
        // not become permitted by arriving under the newer program id.
        val reason =
            solVaultRejection(
                tokenIx(3, listOf(WRAPPED_SOL_ACCOUNT), programId = TOKEN_2022_PROGRAM)
            )
        assertTrue(reason.contains("SPL token instruction 3"), reason)
    }

    @Test
    fun `an SPL token instruction carrying no discriminator at all is refused`() {
        val reason = solVaultRejection(KaminoTxInstruction(TOKEN_PROGRAM, ByteArray(0)))
        assertTrue(reason.contains("no SPL token instruction to check"), reason)
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
        val reason = rejection(listOf(kvault(), tokenIx(17, listOf(TOKEN_ACCOUNT)), memo))
        assertTrue(reason.contains("only the wrapped-SOL vault"), reason)
    }

    @Test
    fun `crediting a wrap is refused when the wrapped-SOL account could not be derived`() {
        // Same posture the transfer already took: with nothing to compare against, refuse rather
        // than wave through.
        val reason =
            solVaultRejection(tokenIx(17, listOf(WRAPPED_SOL_ACCOUNT)), tokenAccount = null)
        assertTrue(reason.contains("could not be derived"), reason)
    }

    private companion object {
        const val DEFAULT_FEE_PAYER = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"
        const val OTHER_ACCOUNT = "SomeoneElsesAccount11111111111111111111111"
        const val SYSTEM_PROGRAM = "11111111111111111111111111111111"
        const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val TOKEN_2022_PROGRAM = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
        const val ASSOCIATED_TOKEN_PROGRAM = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"

        /**
         * The accounts [DEFAULT_FEE_PAYER] actually owns, as the captured fixtures name them: its
         * USDC account and its Steakhouse share account, and on the SOL side its wrapped-SOL and
         * Allez SOL share accounts. Real derivations rather than placeholders, so these tests
         * compare the same addresses the preparer hands the validator on device.
         */
        const val TOKEN_ACCOUNT = "4nkDh9aubXGWVeWfnnWsQ24rGm2RVmRLabRXAQwsEGpB"

        const val SHARE_ACCOUNT = "GSayQpRaoh1LFdBbja4vensNKDfihcixzCcQShKMCdMJ"
        const val WRAPPED_SOL_ACCOUNT = "GppmkdEmuqNgS7uY5SSN3gXEamJrcPG9197wBdQ37NLc"
        const val SOL_SHARE_ACCOUNT = "Hq6N6sNE638VLULNEeAZRTMFmYtsG9ZLLPJYefxwPNWf"

        /** Anchor discriminators: the first eight bytes of `sha256("global:<name>")`. */
        const val KVAULT_DEPOSIT = "f223c68952e1f2b6"

        const val KVAULT_WITHDRAW = "b712469c946da122"
        const val KVAULT_WITHDRAW_FROM_AVAILABLE = "1383709baadc2239"
        const val FARMS_INITIALIZE_USER = "6f11b9fa3c7a26fe"
        const val FARMS_STAKE = "ceb0ca12c8d1b36c"
        const val FARMS_UNSTAKE = "5a5f6b2acd7c32e1"
        const val FARMS_WITHDRAW_UNSTAKED_DEPOSITS = "2466bb31dc248443"

        /** Not one this app performs — the point of the test that uses it. */
        const val FARMS_TRANSFER_OWNERSHIP = "41b1d749352d632f"

        /** 1 USDC, at the Steakhouse vault's six decimals. */
        val AMOUNT: BigInteger = BigInteger.valueOf(1_000_000)

        /** 0.05 SOL, the figure the captured Allez SOL deposit wraps. */
        val SOL_AMOUNT: BigInteger = BigInteger.valueOf(50_000_000)

        val U64_MAX: BigInteger = BigInteger("18446744073709551615")

        fun bytes(hex: String) =
            ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

        fun littleEndian(value: BigInteger, bytes: Int) =
            ByteArray(bytes) { value.shiftRight(8 * it).and(BigInteger.valueOf(0xFF)).toByte() }

        /** The farms program holds stake at `WAD`, so its amounts are share base units × 10^18. */
        fun unstakeScaled(shares: BigInteger): BigInteger = shares.multiply(BigInteger.TEN.pow(18))
    }
}
