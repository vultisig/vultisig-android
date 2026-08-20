package com.vultisig.wallet.data.blockchain.solana.kamino

import java.math.BigInteger

/**
 * One decoded instruction, reduced to what validation reasons about.
 *
 * [accounts] carries the resolved account addresses, not indices: without them the validator can
 * only ask *which programs* run, and a compromised response could add a transfer to an attacker
 * using a program the transaction legitimately needs anyway.
 */
data class KaminoTxInstruction(
    val programId: String,
    val data: ByteArray,
    val accounts: List<String> = emptyList(),
) {
    // ByteArray identity would make two structurally equal instructions compare unequal, which
    // matters because tests and set comparisons rely on value semantics here.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is KaminoTxInstruction &&
                programId == other.programId &&
                data.contentEquals(other.data) &&
                accounts == other.accounts)

    override fun hashCode(): Int =
        31 * (31 * programId.hashCode() + data.contentHashCode()) + accounts.hashCode()
}

/** Why a prepared Kamino transaction was refused. */
class KaminoTransactionRejected(message: String) : IllegalStateException(message)

/**
 * Checks a transaction the app is about to sign against what the app itself intended.
 *
 * The point is that Kamino builds these transactions, so nothing fetched from Kamino can be used to
 * validate one — the check would be circular, and a single compromised response could supply a
 * matching pair. Everything checked here comes from the local registry or from the app's own
 * intent.
 *
 * Kept free of JNI so the rules are unit-testable; decoding and address derivation are the caller's
 * job.
 */
object KaminoTransactionValidator {

    /** Programs a curated vault's deposit or withdraw is allowed to touch. */
    private val ALWAYS_ALLOWED =
        setOf(
            "11111111111111111111111111111111", // System
            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", // Token
            "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb", // Token-2022
            "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL", // Associated Token
            KaminoComputeBudget.PROGRAM_ID,
            KaminoVaultRegistry.PROGRAM_ID,
            KaminoVaultRegistry.FARMS_PROGRAM_ID,
            KaminoAttributionMemo.MEMO_PROGRAM_ID,
        )

    private val TAG_BYTES = KaminoAttributionMemo.TAG.toByteArray(Charsets.US_ASCII)

    private const val SYSTEM_PROGRAM_ID = "11111111111111111111111111111111"
    private const val TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    private const val TOKEN_2022_PROGRAM_ID = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
    private const val ASSOCIATED_TOKEN_PROGRAM_ID = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"

    /** `CloseAccount` and `SyncNative` in the SPL Token instruction enum. */
    private const val TOKEN_CLOSE_ACCOUNT = 9
    private const val TOKEN_SYNC_NATIVE = 17

    /** `SystemInstruction::Transfer`. */
    private const val SYSTEM_TRANSFER = 2

    /** `AssociatedTokenAccountInstruction::CreateIdempotent`. */
    private const val CREATE_IDEMPOTENT = 1

    /** What the kVault program reads as "withdraw everything". */
    private val U64_MAX = BigInteger("18446744073709551615")

    /** The farms program holds stake at `WAD`: its `u128` amounts are share base units × 10^18. */
    private val FARMS_STAKE_SCALE = BigInteger.TEN.pow(18)

    private const val ANCHOR_DISCRIMINATOR = 8

    /**
     * Anchor discriminators — the first eight bytes of `sha256("global:<name>")` — for the two
     * programs whose instructions Kamino's builder composes itself rather than reaching through a
     * CPI of its own.
     *
     * Derived from each instruction's name and *then* found in the captured responses, which is
     * what makes them named rather than pasted: a constant copied out of a transaction matches the
     * shape it was copied from and asserts nothing. iOS pins the same seven in
     * `KaminoSolanaInstructions.swift`.
     */
    private val KVAULT_DEPOSIT = anchor("f223c68952e1f2b6")

    private val KVAULT_WITHDRAW = anchor("b712469c946da122")

    /**
     * The same withdraw served entirely out of the vault's liquid buffer. Which of the two arrives
     * is a fact about the vault's balance sheet at build time, not about the request, so both are
     * accepted — and they share an account layout by construction: the program's IDL declares
     * `withdraw`'s accounts as `withdraw_from_available`'s followed by the reserve group.
     */
    private val KVAULT_WITHDRAW_FROM_AVAILABLE = anchor("1383709baadc2239")

    private val FARMS_INITIALIZE_USER = anchor("6f11b9fa3c7a26fe")
    private val FARMS_STAKE = anchor("ceb0ca12c8d1b36c")
    private val FARMS_UNSTAKE = anchor("5a5f6b2acd7c32e1")
    private val FARMS_WITHDRAW_UNSTAKED_DEPOSITS = anchor("2466bb31dc248443")

    /**
     * The four farms instructions above, under the name each is bounded and reported by.
     *
     * One list rather than two: the walk asks whether an instruction is in here and [validate]
     * counts each entry, so a farms instruction cannot become permitted in one place and unbounded
     * in the other.
     */
    private val FARMS_INSTRUCTIONS =
        mapOf(
            "initialize_user" to FARMS_INITIALIZE_USER,
            "stake" to FARMS_STAKE,
            "unstake" to FARMS_UNSTAKE,
            "withdraw_unstaked_deposits" to FARMS_WITHDRAW_UNSTAKED_DEPOSITS,
        )

    /**
     * Positions of the accounts each instruction is checked on.
     *
     * An Anchor instruction's account list is fixed by its IDL — only the trailing
     * `remaining_accounts` vary, which is why the observed lists differ in length between vaults
     * while these prefixes do not. Every index below was read back out of the four captured
     * responses in `KaminoFixtures`, and each is the index iOS checks in
     * `KaminoSolanaInstructions.swift`.
     */
    private const val KVAULT_USER = 0

    private const val KVAULT_DEPOSIT_TOKEN_ACCOUNT = 6

    private const val KVAULT_WITHDRAW_TOKEN_ACCOUNT = 5

    /** Both kVault instructions agree here, which is what lets one check serve either action. */
    private const val KVAULT_SHARE_ACCOUNT = 7

    private const val FARMS_OWNER = 0

    /**
     * Where each farms instruction names the wallet's state in the farm.
     *
     * `initialize_user` is the odd one out because it is the instruction that *creates* the
     * account: the IDL puts the authority, the payer, the owner and the delegatee ahead of it,
     * where the other three take the owner and then the state it already has.
     */
    private const val FARMS_USER_STATE = 1

    private const val FARMS_INITIALIZE_USER_STATE = 4

    private const val FARMS_STAKE_SHARE_ACCOUNT = 4

    private const val FARMS_UNSTAKED_SHARE_DESTINATION = 3

    private const val SYSTEM_TRANSFER_DESTINATION = 1

    private const val CREATED_TOKEN_ACCOUNT = 1

    private const val TOKEN_CLOSE_DESTINATION = 1

    private const val TOKEN_CLOSE_AUTHORITY = 2

    /**
     * @param signerAddress the wallet this app is signing for
     * @param tokenAccount the wallet's associated token account for [vault]'s underlying mint,
     *   derived locally by the caller — on the SOL vault that is its wrapped-SOL account
     * @param shareAccount the wallet's associated token account for [vault]'s share mint, likewise
     *   derived locally: offline it is the only thing that binds a kVault instruction to *this*
     *   vault
     * @param farmUserState the wallet's state in [vault]'s farm, a program address over the two and
     *   so likewise derived locally — what binds a farms instruction to this vault's farm, which
     *   the farm account itself cannot do from a lookup table
     * @param amountBaseUnits what the user asked for, in the unit the instruction carries — the
     *   underlying token for a deposit, shares for a withdraw
     * @throws KaminoTransactionRejected if [decoded] is not a transaction the app is willing to
     *   sign for [vault]
     */
    fun validate(
        decoded: KaminoDecodedTransaction,
        vault: KaminoVault,
        action: KaminoAction,
        signerAddress: String? = null,
        tokenAccount: String? = null,
        shareAccount: String? = null,
        farmUserState: String? = null,
        amountBaseUnits: BigInteger? = null,
    ) {
        val instructions = decoded.instructions
        if (!KaminoVaultRegistry.isAllowed(vault.address)) {
            reject("${vault.address} is not a vault this app transacts with")
        }

        if (instructions.isEmpty()) reject("transaction carries no instructions")

        instructions.forEachIndexed { index, instruction ->
            if (instruction.programId !in ALWAYS_ALLOWED) {
                reject("instruction $index invokes unexpected program ${instruction.programId}")
            }
        }

        // The whole point of the transaction. Without it, whatever the app is about to sign is not
        // the deposit or withdraw the user asked for.
        //
        // Exactly one, not merely at least one: every amount below is checked against the figure on
        // screen instruction by instruction, so a second kVault instruction carrying that same
        // figure would pass each check on its own and move the money twice.
        val vaultInstructions =
            instructions.count { it.programId == KaminoVaultRegistry.PROGRAM_ID }
        when {
            vaultInstructions == 0 ->
                reject("transaction never invokes the kVault program (expected for $action)")
            vaultInstructions > 1 ->
                reject("expected exactly one kVault instruction, found $vaultInstructions")
        }

        // Same reasoning on the System side: the wrap is pinned to the deposit amount, so two of
        // them wrap it twice while the screen still shows one figure.
        val systemInstructions = instructions.count { it.programId == SYSTEM_PROGRAM_ID }
        if (systemInstructions > 1) {
            reject("expected at most one System instruction, found $systemInstructions")
        }

        // And one of each on the farms side, where the same reasoning meets a program that carries
        // more than one instruction. Every farms check below is per-instruction too: two unstakes
        // each sitting within the bound release twice what the withdraw burns, and the surplus
        // leaves the farm to sit in the share account earning nothing while the screen still shows
        // one figure. A second `initialize_user` funds another farm's user state out of the
        // wallet's rent. iOS bounds each of the four the same way — every farms step in
        // `KaminoInstructionSequence.expected` is `repeatable: false`.
        val farmsInstructions =
            instructions.filter { it.programId == KaminoVaultRegistry.FARMS_PROGRAM_ID }
        FARMS_INSTRUCTIONS.forEach { (name, discriminator) ->
            val found = farmsInstructions.count { it.data.startsWith(discriminator) }
            if (found > 1) {
                reject("expected at most one farms $name instruction, found $found")
            }
        }

        val memos = instructions.filter { it.programId == KaminoAttributionMemo.MEMO_PROGRAM_ID }
        when {
            memos.isEmpty() -> reject("attribution memo is missing")
            memos.size > 1 -> reject("expected exactly one memo, found ${memos.size}")
        }

        val memo = memos.single()
        // A memo naming accounts is the Memo program *attesting* that they signed. Attribution is
        // not that, and the app's own injection names none, so an account list means this memo came
        // from somewhere else.
        if (memo.accounts.isNotEmpty()) {
            reject("attribution memo must name no accounts, found ${memo.accounts.size}")
        }
        if (!memo.data.contentEquals(TAG_BYTES)) {
            // Any memo other than the exact tag is a refusal: an arbitrary memo riding along on a
            // signed transaction is a channel the app did not open.
            reject(
                "memo carries unexpected data (expected \"${KaminoAttributionMemo.TAG}\", " +
                    "found ${memo.data.size} byte(s))"
            )
        }

        // Pinned so the golden coverage and the on-chain shape agree: compute budget is injected
        // before the memo, which leaves the memo unambiguously last.
        if (instructions.last().programId != KaminoAttributionMemo.MEMO_PROGRAM_ID) {
            reject("memo must be the final instruction")
        }

        rejectAnotherAccountsAuthority(decoded.feePayer, signerAddress)
        // Ahead of the walk below, which checks the same `u64` against the amount that was asked
        // for. A transaction carrying the sentinel fails both, and the sentinel is the refusal
        // worth naming: it is the one amount whose consequence is the entire position.
        rejectTheWithdrawEverythingSentinel(instructions)
        rejectValueMovementAwayFromTheSigner(
            instructions = instructions,
            vault = vault,
            action = action,
            signerAddress = signerAddress,
            tokenAccount = tokenAccount,
            shareAccount = shareAccount,
            farmUserState = farmUserState,
            amountBaseUnits = amountBaseUnits,
        )
        rejectAForeignComputeBudget(instructions, vault, action)
        rejectAnythingButOneEmptySignatureSlot(decoded)
    }

    /**
     * Refuses a compute budget that is not the one this app injects.
     *
     * The budget is what the transaction will be charged: price × limit, in lamports, on top of the
     * base fee. Both devices quote it on their verify screens — the initiating one from the values
     * it recorded, a co-signing one from the values relayed beside the bytes — and neither of those
     * is the budget the runtime reads. That one is here, in the instructions.
     *
     * So the shape is pinned rather than merely allow-listed. The program alone being permitted is
     * what let a deposit legitimately carry a ComputeBudget instruction while its argument said
     * anything at all: a price is an unbounded `u64` multiplied by a six-figure limit, so an
     * unchecked one is an unbounded fee sitting under a fee row that clamps its own display into
     * [KaminoComputeBudget.MAX_UNIT_PRICE]. iOS refuses the same disagreement
     * (`KaminoVerifyPresentation.priorityFeeAgrees`).
     *
     * Position is pinned too, not for the fee's sake but because the layout is a cross-platform
     * contract: iOS emits the limit at 0 and the price at 1 and reads the remaining instructions
     * positionally, so anything else is a transaction an iPhone co-signer will not join.
     */
    private fun rejectAForeignComputeBudget(
        instructions: List<KaminoTxInstruction>,
        vault: KaminoVault,
        action: KaminoAction,
    ) {
        val budget =
            KaminoComputeBudget.readFrom(instructions)
                ?: reject(
                    "transaction carries no compute budget, so it would run against the runtime " +
                        "default and abort"
                )
        if (budget == KaminoComputeBudget.MALFORMED) {
            reject("transaction carries a compute budget this app cannot read")
        }

        val budgetIndices =
            instructions.indices.filter {
                instructions[it].programId == KaminoComputeBudget.PROGRAM_ID
            }
        if (
            budgetIndices !=
                listOf(KaminoComputeBudget.UNIT_LIMIT_INDEX, KaminoComputeBudget.UNIT_PRICE_INDEX)
        ) {
            reject(
                "compute budget must be the leading two instructions, found it at $budgetIndices"
            )
        }
        if (
            KaminoComputeBudget.unitLimitArgument(
                instructions[KaminoComputeBudget.UNIT_LIMIT_INDEX].data
            ) == null
        ) {
            reject(
                "instruction ${KaminoComputeBudget.UNIT_LIMIT_INDEX} must be the compute-unit limit"
            )
        }

        val expectedLimit = KaminoComputeBudget.unitLimitFor(vault, action)
        if (budget.limit != expectedLimit) {
            reject(
                "compute-unit limit is ${budget.limit} rather than the $expectedLimit this app " +
                    "reserves for a $action"
            )
        }
        // `unitPriceFor` is the clamp itself, so a price it does not move is a price already inside
        // the range — the same range iOS clamps into and the only one its decoder accepts.
        if (budget.price != KaminoComputeBudget.unitPriceFor(budget.price)) {
            reject(
                "compute-unit price ${budget.price} is outside " +
                    "[${KaminoComputeBudget.FALLBACK_UNIT_PRICE}, " +
                    "${KaminoComputeBudget.MAX_UNIT_PRICE}]"
            )
        }
    }

    /**
     * Refuses anything but a transaction with one still-empty signature slot.
     *
     * The raw-signing path splices this vault's signature into slot 0 and leaves the message and
     * every further slot exactly as received — which is right for a dApp co-sign and wrong here. A
     * second required signer means the app signs and broadcasts something incomplete, or something
     * completed by whoever supplied the bytes; a slot that already holds bytes means a signature
     * over some message this device never saw riding along with its own.
     *
     * Fail-closed on both devices. iOS gates the same decode with `validateUnsignedSingleSigner`.
     */
    private fun rejectAnythingButOneEmptySignatureSlot(decoded: KaminoDecodedTransaction) {
        if (decoded.requiredSignatures != 1) {
            reject(
                "transaction declares ${decoded.requiredSignatures} required signatures; only " +
                    "signer 0's slot is ever filled"
            )
        }
        if (!decoded.isUnsigned) {
            reject("transaction already carries a signature in a slot this app does not write")
        }
    }

    /**
     * Refuses a kVault instruction whose amount argument is `u64::MAX`, which the program reads as
     * *withdraw everything* rather than as a share count.
     *
     * **This app cannot produce one.** The withdraw form's maximum is derived to sit strictly below
     * the balance precisely so the API never answers with the sentinel, and an over-request is
     * refused rather than clamped. So a transaction carrying it did not come from here — and it is
     * the one amount whose consequence is the entire position rather than the figure on screen.
     * Refused on every device, initiating or co-signing: disclosing it while allowing the signature
     * would put the whole position behind a label.
     *
     * Confirmed empirically: asking Kamino to withdraw 0.5 shares from a wallet holding nothing
     * returns an instruction carrying exactly this.
     */
    private fun rejectTheWithdrawEverythingSentinel(instructions: List<KaminoTxInstruction>) {
        instructions.forEachIndexed { index, instruction ->
            if (instruction.programId != KaminoVaultRegistry.PROGRAM_ID) return@forEachIndexed
            val amount = anchorArgument(instruction.data, bytes = 8) ?: return@forEachIndexed
            if (amount == U64_MAX) {
                reject(
                    "instruction $index carries the withdraw-everything sentinel, which this app " +
                        "never produces"
                )
            }
        }
    }

    /**
     * Refuses instructions that could move value somewhere the deposit or withdraw has no reason
     * to.
     *
     * Program allow-listing alone is not enough: a deposit legitimately needs the System and Token
     * programs, so a compromised response could add a transfer to an attacker and still invoke
     * kVault and end with the memo. These checks are about *what* those programs are asked to do,
     * with what, and on whose accounts.
     *
     * Every arm is an allow-list, and deliberately so. A deny-list of the opcodes that look
     * dangerous is no control at all here — the same compromised response picks the opcode, so
     * everything not thought of is waved through, and the ones that matter most do not look like
     * transfers: `Assign` hands the wallet's system account to another program, `SetAuthority`
     * hands over a token account, `farms::transfer_ownership` hands over a staked position, and
     * none of them moves a lamport itself.
     *
     * The permitted set is what live Kamino responses carry. A wrapped-SOL vault deposit wraps with
     * `System::Transfer` then `Token::SyncNative`; its withdraw unwraps with `Token::CloseAccount`.
     * A plain-token vault carries none of the three — it moves its tokens entirely through the
     * kVault program's own CPI.
     */
    private fun rejectValueMovementAwayFromTheSigner(
        instructions: List<KaminoTxInstruction>,
        vault: KaminoVault,
        action: KaminoAction,
        signerAddress: String?,
        tokenAccount: String?,
        shareAccount: String?,
        farmUserState: String?,
        amountBaseUnits: BigInteger?,
    ) {
        instructions.forEachIndexed { index, instruction ->
            when (instruction.programId) {
                TOKEN_PROGRAM_ID,
                TOKEN_2022_PROGRAM_ID ->
                    rejectUnlessPermittedTokenInstruction(
                        index = index,
                        instruction = instruction,
                        vault = vault,
                        signerAddress = signerAddress,
                        tokenAccount = tokenAccount,
                    )

                SYSTEM_PROGRAM_ID ->
                    rejectUnlessWrap(
                        index = index,
                        instruction = instruction,
                        vault = vault,
                        action = action,
                        tokenAccount = tokenAccount,
                        amountBaseUnits = amountBaseUnits,
                    )

                KaminoVaultRegistry.PROGRAM_ID ->
                    rejectUnlessTheVaultMoveThisAppAskedFor(
                        index = index,
                        instruction = instruction,
                        action = action,
                        signerAddress = signerAddress,
                        tokenAccount = tokenAccount,
                        shareAccount = shareAccount,
                        amountBaseUnits = amountBaseUnits,
                    )

                ASSOCIATED_TOKEN_PROGRAM_ID ->
                    rejectUnlessCreatingOneOfTheWalletsOwnAccounts(
                        index = index,
                        instruction = instruction,
                        tokenAccount = tokenAccount,
                        shareAccount = shareAccount,
                    )

                KaminoVaultRegistry.FARMS_PROGRAM_ID ->
                    rejectUnlessPermittedFarmsInstruction(
                        index = index,
                        instruction = instruction,
                        action = action,
                        signerAddress = signerAddress,
                        shareAccount = shareAccount,
                        farmUserState = farmUserState,
                        amountBaseUnits = amountBaseUnits,
                    )
            }
        }
    }

    /**
     * The one instruction that *is* the transaction, tied to the vault on screen and to the figure
     * next to it.
     *
     * The vault account itself cannot do the tying here. It is an address-lookup-table entry in
     * every captured response and this decode resolves no tables, so comparing it would be a check
     * that passes by being unreadable. The user's own accounts can: the share account and the token
     * account are static keys in all four captured shapes, both are associated token addresses over
     * mints the registry pins, and the caller derives them locally. A response that substituted
     * another vault — including one it made itself through the permissionless `init_vault` — would
     * have to name a share account this wallet does not own for that vault's mint. iOS identifies
     * the vault the same way round, and for the same reason: `KaminoTransactionDecoder.swift:90`.
     *
     * The amount is checked against the figure the app sent rather than against anything the
     * response echoed back, which is what makes it a check and not a restatement.
     */
    private fun rejectUnlessTheVaultMoveThisAppAskedFor(
        index: Int,
        instruction: KaminoTxInstruction,
        action: KaminoAction,
        signerAddress: String?,
        tokenAccount: String?,
        shareAccount: String?,
        amountBaseUnits: BigInteger?,
    ) {
        val tokenAccountSlot =
            when (action) {
                KaminoAction.DEPOSIT -> {
                    if (!instruction.data.startsWith(KVAULT_DEPOSIT)) {
                        reject("instruction $index is not the kVault deposit this app asked for")
                    }
                    KVAULT_DEPOSIT_TOKEN_ACCOUNT
                }

                KaminoAction.WITHDRAW -> {
                    val isWithdraw =
                        instruction.data.startsWith(KVAULT_WITHDRAW) ||
                            instruction.data.startsWith(KVAULT_WITHDRAW_FROM_AVAILABLE)
                    if (!isWithdraw) {
                        reject("instruction $index is not the kVault withdraw this app asked for")
                    }
                    KVAULT_WITHDRAW_TOKEN_ACCOUNT
                }
            }

        rejectUnlessAccountIs(
            index,
            instruction,
            KVAULT_USER,
            signerAddress,
            "the authority this vault move runs under",
        )
        // The share account first: it is what says *which vault* this is, so a substituted
        // `vault_state` is named as that rather than as a token account that happens to disagree.
        rejectUnlessAccountIs(
            index,
            instruction,
            KVAULT_SHARE_ACCOUNT,
            shareAccount,
            "the wallet's share account for this vault",
        )
        rejectUnlessAccountIs(
            index,
            instruction,
            tokenAccountSlot,
            tokenAccount,
            "the wallet's token account for this vault",
        )
        rejectUnlessAmountIs(index, instruction, amountBaseUnits)
    }

    /**
     * Every farms instruction a curated vault's deposit or withdraw has cause to carry, and nothing
     * else.
     *
     * A deposit stakes its shares into the vault's farm, so the shares never land in the wallet as
     * a token balance — which is why the farms program is allow-listed as a program at all. That
     * made it the one program here whose instructions went unread: `transfer_ownership` hands the
     * whole staked position to an account that never signs, and nothing about it looks like a
     * transfer. iOS names exactly these four at `KaminoInstructionSequence.swift:301`, and the
     * shapes match its live template — a deposit stakes, a withdraw unstakes, neither does both.
     *
     * The authority alone does not bind one of these to this vault, and the farm account cannot: it
     * is an address-lookup-table entry in every captured response and this decode resolves no
     * tables, so comparing it would be a check that passes by being unreadable. The farms **user
     * state** does bind it. Its address is a program address over the farm and the owner, it is a
     * static key in both captured deposits, and the caller derives it locally — so an instruction
     * naming another farm's state, or another holder's, is refused. Without that check
     * `farms::stake` sweeps the wallet's whole share balance into a farm the response made, since
     * the argument it carries is the whole-balance sentinel. iOS derives the same address and pins
     * it at `KaminoTransactionValidator.swift:1006`; it is pinned on all four here rather than on
     * the two it names, because the farm slot it checks alongside is unreadable offline.
     *
     * Everything below is per-instruction; how many of each may appear at all is bounded in
     * [validate], since a check an instruction passes on its own it also passes twice.
     */
    private fun rejectUnlessPermittedFarmsInstruction(
        index: Int,
        instruction: KaminoTxInstruction,
        action: KaminoAction,
        signerAddress: String?,
        shareAccount: String?,
        farmUserState: String?,
        amountBaseUnits: BigInteger?,
    ) {
        val data = instruction.data
        val isOneWePerform = FARMS_INSTRUCTIONS.values.any { data.startsWith(it) }
        if (!isOneWePerform) {
            reject(
                "instruction $index is a farms instruction no Kamino deposit or withdraw performs"
            )
        }
        rejectUnlessAccountIs(index, instruction, FARMS_OWNER, signerAddress, "the farm authority")
        rejectUnlessAccountIs(
            index,
            instruction,
            if (data.startsWith(FARMS_INITIALIZE_USER)) FARMS_INITIALIZE_USER_STATE
            else FARMS_USER_STATE,
            farmUserState,
            "this wallet's state in the vault's farm",
        )

        when {
            data.startsWith(FARMS_INITIALIZE_USER) -> {
                rejectUnlessDeposit(index, action, "creates the wallet's farm state")
            }

            data.startsWith(FARMS_STAKE) -> {
                rejectUnlessDeposit(index, action, "stakes shares into the farm")
                rejectUnlessAccountIs(
                    index,
                    instruction,
                    FARMS_STAKE_SHARE_ACCOUNT,
                    shareAccount,
                    "the wallet's share account for this vault",
                )
                // Kamino stakes the whole share balance rather than the amount just minted, so the
                // argument is the sentinel. A different value is a behaviour change rather than a
                // variation, and is worth stopping on.
                if (anchorArgument(data, bytes = 8) != U64_MAX) {
                    reject(
                        "instruction $index stakes an amount this app has never seen Kamino ask for"
                    )
                }
            }

            data.startsWith(FARMS_UNSTAKE) -> {
                rejectUnlessWithdraw(index, action, "releases shares from the farm")
                // The exact figure is `requested − alreadyUnstaked`, and the second term is a
                // balance this app does not hold at prepare time. So what is checked is the bound
                // that needs no balance: a withdraw cannot legitimately take more out of the farm
                // than it burns. Released shares the withdraw then leaves behind are out of the
                // farm, no longer earning, and invisible on a screen that shows an amount.
                //
                // The argument is a `u128` scaled by 10^18, the only one in this feature. Reading
                // it as a `u64` would take the low half and compare a number that means nothing.
                val scaled =
                    anchorArgument(data, bytes = 16)
                        ?: reject("instruction $index carries no farms amount to check")
                if (amountBaseUnits == null) {
                    reject(
                        "instruction $index releases shares but the requested amount could not be " +
                            "read, so how many it releases cannot be checked"
                    )
                }
                if (scaled > amountBaseUnits.multiply(FARMS_STAKE_SCALE)) {
                    reject(
                        "instruction $index releases more shares from the farm than the withdraw " +
                            "burns"
                    )
                }
            }

            data.startsWith(FARMS_WITHDRAW_UNSTAKED_DEPOSITS) -> {
                rejectUnlessWithdraw(index, action, "moves released shares out of the farm")
                // Where the released shares land, and the account the vault withdraw then burns
                // from. A destination belonging to anyone else hands the position over.
                rejectUnlessAccountIs(
                    index,
                    instruction,
                    FARMS_UNSTAKED_SHARE_DESTINATION,
                    shareAccount,
                    "the wallet's share account for this vault",
                )
            }
        }
    }

    /**
     * The accounts a transaction creates are the wallet's own two, and nothing else.
     *
     * Kamino creates whatever the operation needs — the share account a deposit mints into, the
     * wrapped-SOL account it wraps through, the payout account a withdraw pays into — and each is
     * created idempotently, so one that already exists is left alone. Every one of them is an
     * account this app derives for itself, which makes the check exact.
     *
     * Rent is what makes it worth checking rather than waving through: creation is funded by the
     * fee payer, so an account created for someone else is the wallet paying rent into an address
     * the response chose, on the signature the message already carries.
     */
    private fun rejectUnlessCreatingOneOfTheWalletsOwnAccounts(
        index: Int,
        instruction: KaminoTxInstruction,
        tokenAccount: String?,
        shareAccount: String?,
    ) {
        val discriminator =
            instruction.data.firstOrNull()?.toInt()?.and(0xFF)
                ?: reject("instruction $index carries no account-creation instruction to check")
        if (discriminator != CREATE_IDEMPOTENT) {
            reject(
                "instruction $index is associated-token instruction $discriminator, which no " +
                    "Kamino deposit or withdraw performs"
            )
        }
        if (tokenAccount == null || shareAccount == null) {
            reject(
                "instruction $index creates a token account but the wallet's own accounts could " +
                    "not be derived, so the account it creates cannot be checked"
            )
        }
        val created = instruction.accounts.getOrNull(CREATED_TOKEN_ACCOUNT)
        if (created != tokenAccount && created != shareAccount) {
            reject(
                "instruction $index creates $created, which is not one of the wallet's own accounts"
            )
        }
    }

    /**
     * The only SPL Token instructions one of these transactions has cause to carry at top level.
     *
     * `SyncNative` credits the lamports a wrap just sent, and is pinned to the wrapped-SOL account
     * derived locally from the signer: the deposit carrying it was captured, so its shape is known
     * exactly.
     *
     * `CloseAccount` returns what an unwrap released, and is checked by *recipient* rather than by
     * subject — both the destination it pays out to and the authority closing it must be the
     * signer. The looser subject is deliberate. Only the unstaked withdraw could be captured, and a
     * staked exit may close accounts this app has never seen; pinning the subject would refuse
     * those, while pinning the recipient still refuses the redirect the check exists for. Nothing
     * is given up by it: the wallet can only close accounts it owns, and every lamport released
     * lands on the wallet either way.
     *
     * Comparisons are exact, so an account the response hides behind its own address lookup table —
     * which decodes to [KaminoTransactionDecoder.UNKNOWN_ACCOUNT] — fails them rather than skipping
     * them.
     */
    private fun rejectUnlessPermittedTokenInstruction(
        index: Int,
        instruction: KaminoTxInstruction,
        vault: KaminoVault,
        signerAddress: String?,
        tokenAccount: String?,
    ) {
        val discriminator =
            instruction.data.firstOrNull()?.toInt()?.and(0xFF)
                ?: reject("instruction $index carries no SPL token instruction to check")

        when (discriminator) {
            TOKEN_SYNC_NATIVE -> {
                if (vault.tokenMint != KaminoVaultRegistry.WRAPPED_SOL_MINT) {
                    reject(
                        "instruction $index credits a wrap, which only the wrapped-SOL vault has " +
                            "cause to do"
                    )
                }
                if (tokenAccount == null) {
                    reject(
                        "instruction $index credits a wrap but the wrapped-SOL account could not " +
                            "be derived, so the account it credits cannot be checked"
                    )
                }
                val subject = instruction.accounts.firstOrNull()
                if (subject != tokenAccount) {
                    reject(
                        "instruction $index credits $subject rather than the wallet's wrapped-SOL " +
                            "account"
                    )
                }
            }

            TOKEN_CLOSE_ACCOUNT -> {
                // Closing pays out the account's entire lamport balance, which on a full exit is
                // the whole withdrawn amount, so the recipient is what has to be pinned.
                if (signerAddress == null) {
                    reject(
                        "instruction $index closes a token account but the signer is unknown, so " +
                            "its recipient cannot be checked"
                    )
                }
                val destination = instruction.accounts.getOrNull(TOKEN_CLOSE_DESTINATION)
                if (destination != signerAddress) {
                    reject(
                        "instruction $index pays a closed token account out to $destination " +
                            "rather than to the wallet"
                    )
                }
                val owner = instruction.accounts.getOrNull(TOKEN_CLOSE_AUTHORITY)
                if (owner != signerAddress) {
                    reject(
                        "instruction $index closes a token account under the authority of $owner " +
                            "rather than the wallet's"
                    )
                }
            }

            else ->
                reject(
                    "instruction $index is SPL token instruction $discriminator, which no Kamino " +
                        "deposit or withdraw performs"
                )
        }
    }

    /**
     * Lamports only ever move to wrap SOL, only for the wrapped-SOL vault, only into the wallet's
     * own wrapped-SOL account and only for the amount being deposited.
     *
     * Every other `SystemInstruction` is refused rather than ignored. `Assign` on the signer's own
     * account reassigns it to a program the response chose, and `CreateAccountWithSeed` funds an
     * account it chose with an amount it chose — both authorised by the fee-payer signature this
     * message already carries, and neither one a transfer.
     *
     * The lamport figure is checked too, not just the destination. Reading only the four
     * discriminator bytes leaves the amount unbound, and a response is free to wrap the whole SOL
     * balance while the screen shows the typed amount: the deposit consumes what was asked for and
     * the remainder sits in a wrapped-SOL account with no screen in this app. iOS pins the same
     * equality in `KaminoTransactionDecoder.swift:398`.
     */
    private fun rejectUnlessWrap(
        index: Int,
        instruction: KaminoTxInstruction,
        vault: KaminoVault,
        action: KaminoAction,
        tokenAccount: String?,
        amountBaseUnits: BigInteger?,
    ) {
        val discriminator =
            littleEndianInt(instruction.data.take(4))
                ?: reject("instruction $index carries no System instruction to check")

        if (discriminator != SYSTEM_TRANSFER) {
            reject(
                "instruction $index is System instruction $discriminator, which no Kamino deposit " +
                    "or withdraw performs"
            )
        }
        if (vault.tokenMint != KaminoVaultRegistry.WRAPPED_SOL_MINT) {
            reject(
                "instruction $index moves lamports, which only the wrapped-SOL vault has cause to do"
            )
        }
        rejectUnlessDeposit(index, action, "moves lamports")
        // Permitted, but only to one address: the wrapped-SOL account derived from the signer and
        // the vault's own mint.
        //
        // An earlier version accepted any destination the kVault instruction also named, which is
        // no check at all against a compromised response — the same response chooses that
        // instruction's account list, so it could name an attacker there and have the transfer
        // waved through. The expected address is derived locally by the caller and compared
        // exactly.
        if (tokenAccount == null) {
            reject(
                "instruction $index moves lamports but the wrapped-SOL account could not be " +
                    "derived, so its destination cannot be checked"
            )
        }
        val destination = instruction.accounts.getOrNull(SYSTEM_TRANSFER_DESTINATION)
        if (destination != tokenAccount) {
            reject(
                "instruction $index moves lamports to $destination rather than to the wallet's " +
                    "wrapped-SOL account"
            )
        }
        if (amountBaseUnits == null) {
            reject(
                "instruction $index moves lamports but the requested amount could not be read, so " +
                    "how many it moves cannot be checked"
            )
        }
        val lamports =
            littleEndianLong(instruction.data, offset = 4)
                ?: reject("instruction $index carries no lamport amount to check")
        if (lamports != amountBaseUnits) {
            reject(
                "instruction $index wraps $lamports lamports rather than the $amountBaseUnits " +
                    "this app asked to deposit"
            )
        }
    }

    /**
     * The transaction must be the wallet's own.
     *
     * Read from the message's account key 0, which is the fee payer and first required signer. An
     * earlier version of this took the first account of the first instruction, which is a different
     * thing: an instruction that happens to name the wallet first says nothing about who authorises
     * the transaction, so a message paid for by another account would have passed.
     */
    private fun rejectAnotherAccountsAuthority(feePayer: String?, signerAddress: String?) {
        if (signerAddress == null || feePayer == null) return
        if (feePayer != signerAddress) {
            reject("transaction is authorised by $feePayer rather than by the wallet")
        }
    }

    /**
     * Compares slot [slot] of [instruction] against an address the caller derived locally.
     *
     * A null [expected] is a refusal rather than a skip, and so is an instruction too short to have
     * the slot: with nothing to compare against, "cannot tell" is not "not a mismatch".
     */
    private fun rejectUnlessAccountIs(
        index: Int,
        instruction: KaminoTxInstruction,
        slot: Int,
        expected: String?,
        role: String,
    ) {
        if (expected == null) {
            reject(
                "instruction $index names $role, which could not be derived locally, so the " +
                    "account it uses cannot be checked"
            )
        }
        val actual =
            instruction.accounts.getOrNull(slot)
                ?: reject("instruction $index names too few accounts to check $role")
        if (actual != expected) {
            reject(
                "instruction $index uses $actual as $role rather than the wallet's own $expected"
            )
        }
    }

    /** Pins an Anchor instruction's `u64` argument to the figure the app asked for. */
    private fun rejectUnlessAmountIs(
        index: Int,
        instruction: KaminoTxInstruction,
        expected: BigInteger?,
    ) {
        if (expected == null) {
            reject(
                "instruction $index moves an amount but the figure this app asked for could not " +
                    "be read, so it cannot be checked"
            )
        }
        val actual =
            anchorArgument(instruction.data, bytes = 8)
                ?: reject("instruction $index carries no amount to check")
        if (actual != expected) {
            reject(
                "instruction $index moves $actual base units rather than the $expected this app " +
                    "asked for"
            )
        }
    }

    private fun rejectUnlessDeposit(index: Int, action: KaminoAction, detail: String) {
        if (action != KaminoAction.DEPOSIT) {
            reject("instruction $index $detail, which no Kamino withdraw performs")
        }
    }

    private fun rejectUnlessWithdraw(index: Int, action: KaminoAction, detail: String) {
        if (action != KaminoAction.WITHDRAW) {
            reject("instruction $index $detail, which no Kamino deposit performs")
        }
    }

    /**
     * The little-endian argument of [bytes] bytes that follows an Anchor discriminator. Null when
     * the instruction is too short to carry one.
     */
    private fun anchorArgument(data: ByteArray, bytes: Int): BigInteger? {
        if (data.size < ANCHOR_DISCRIMINATOR + bytes) return null
        var value = BigInteger.ZERO
        for (offset in bytes - 1 downTo 0) {
            value =
                value
                    .shiftLeft(8)
                    .or(BigInteger.valueOf(data[ANCHOR_DISCRIMINATOR + offset].toLong() and 0xFF))
        }
        return value
    }

    private fun littleEndianInt(bytes: List<Byte>): Int? {
        if (bytes.size < 4) return null
        return bytes.foldIndexed(0) { index, acc, byte ->
            acc or ((byte.toInt() and 0xFF) shl (8 * index))
        }
    }

    /** The `u64` at [offset], little-endian. Null when [data] is too short to carry one. */
    private fun littleEndianLong(data: ByteArray, offset: Int): BigInteger? {
        if (data.size < offset + 8) return null
        var value = BigInteger.ZERO
        for (index in 7 downTo 0) {
            value =
                value.shiftLeft(8).or(BigInteger.valueOf(data[offset + index].toLong() and 0xFF))
        }
        return value
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    /** The bytes [hex] names, which is how every Anchor discriminator above is written. */
    private fun anchor(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun reject(reason: String): Nothing = throw KaminoTransactionRejected(reason)
}
