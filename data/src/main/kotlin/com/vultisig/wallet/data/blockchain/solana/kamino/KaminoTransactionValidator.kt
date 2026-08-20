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
 * Kept free of JNI so the rules are unit-testable; decoding is the caller's job.
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

    /** `Transfer` (3) and `TransferChecked` (12) in the SPL Token instruction enum. */
    private val TOKEN_TRANSFER_DISCRIMINATORS = setOf(3, 12)

    /** `SystemInstruction::Transfer`. */
    private const val SYSTEM_TRANSFER = 2

    /** What the kVault program reads as "withdraw everything". */
    private val U64_MAX = BigInteger("18446744073709551615")

    /** Where [KaminoTransactionPreparer] puts the compute budget, and where iOS expects it. */
    private const val UNIT_LIMIT_INDEX = 0

    private const val UNIT_PRICE_INDEX = UNIT_LIMIT_INDEX + 1

    /**
     * @throws KaminoTransactionRejected if [instructions] is not a transaction the app is willing
     *   to sign for [vault].
     */
    fun validate(
        decoded: KaminoDecodedTransaction,
        vault: KaminoVault,
        action: KaminoAction,
        signerAddress: String? = null,
        wrappedSolAccount: String? = null,
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
        if (instructions.none { it.programId == KaminoVaultRegistry.PROGRAM_ID }) {
            reject("transaction never invokes the kVault program (expected for $action)")
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

        rejectValueMovementAwayFromTheSigner(instructions, vault, wrappedSolAccount)
        rejectAnotherAccountsAuthority(decoded.feePayer, signerAddress)
        rejectTheWithdrawEverythingSentinel(instructions)
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
        if (budgetIndices != listOf(UNIT_LIMIT_INDEX, UNIT_PRICE_INDEX)) {
            reject(
                "compute budget must be the leading two instructions, found it at $budgetIndices"
            )
        }
        if (KaminoComputeBudget.unitLimitArgument(instructions[UNIT_LIMIT_INDEX].data) == null) {
            reject("instruction $UNIT_LIMIT_INDEX must be the compute-unit limit")
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
            val amount = kvaultAmountArgument(instruction.data) ?: return@forEachIndexed
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
     * programs, so a compromised response could add a plain transfer to an attacker and still
     * invoke kVault and end with the memo. These checks are about *what* those programs are asked
     * to do.
     */
    private fun rejectValueMovementAwayFromTheSigner(
        instructions: List<KaminoTxInstruction>,
        vault: KaminoVault,
        wrappedSolAccount: String?,
    ) {
        instructions.forEachIndexed { index, instruction ->
            when (instruction.programId) {
                TOKEN_PROGRAM_ID,
                TOKEN_2022_PROGRAM_ID -> {
                    // The vault moves tokens through its own program's CPI. A *top-level* token
                    // transfer is not part of any shape observed here, and is exactly how a stray
                    // instruction would drain an account the wallet has already authorised.
                    val discriminator = instruction.data.firstOrNull()?.toInt()?.and(0xFF)
                    if (discriminator in TOKEN_TRANSFER_DISCRIMINATORS) {
                        reject(
                            "instruction $index is a top-level SPL token transfer, which no Kamino " +
                                "deposit or withdraw performs"
                        )
                    }
                }

                SYSTEM_PROGRAM_ID -> {
                    // Lamports only ever move to wrap SOL, and only for the wrapped-SOL vault.
                    val discriminator = instruction.data.take(4).let(::littleEndianInt)
                    if (discriminator == SYSTEM_TRANSFER) {
                        if (vault.tokenMint != KaminoVaultRegistry.WRAPPED_SOL_MINT) {
                            reject(
                                "instruction $index moves lamports, which only the wrapped-SOL " +
                                    "vault has cause to do"
                            )
                        }
                        // Permitted, but only to one address: the wrapped-SOL account derived from
                        // the signer and the vault's own mint.
                        //
                        // An earlier version accepted any destination the kVault instruction also
                        // named, which is no check at all against a compromised response — the same
                        // response chooses that instruction's account list, so it could name an
                        // attacker there and have the transfer waved through. The expected address
                        // is
                        // derived locally by the caller and compared exactly.
                        val destination = instruction.accounts.getOrNull(1)
                        if (wrappedSolAccount == null) {
                            reject(
                                "instruction $index moves lamports but the wrapped-SOL account " +
                                    "could not be derived, so its destination cannot be checked"
                            )
                        }
                        if (destination != wrappedSolAccount) {
                            reject(
                                "instruction $index moves lamports to $destination rather than to " +
                                    "the wallet's wrapped-SOL account"
                            )
                        }
                    }
                }
            }
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

    private fun littleEndianInt(bytes: List<Byte>): Int? {
        if (bytes.size < 4) return null
        return bytes.foldIndexed(0) { index, acc, byte ->
            acc or ((byte.toInt() and 0xFF) shl (8 * index))
        }
    }

    private fun reject(reason: String): Nothing = throw KaminoTransactionRejected(reason)
}
