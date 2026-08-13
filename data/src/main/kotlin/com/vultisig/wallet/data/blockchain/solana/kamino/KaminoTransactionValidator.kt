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
            "ComputeBudget111111111111111111111111111111",
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
            val amount = kvaultAmount(instruction.data) ?: return@forEachIndexed
            if (amount == U64_MAX) {
                reject(
                    "instruction $index carries the withdraw-everything sentinel, which this app " +
                        "never produces"
                )
            }
        }
    }

    /**
     * The `u64` amount argument of a kVault instruction: an 8-byte Anchor discriminator followed by
     * the amount, little-endian. Null when the instruction is too short to carry one.
     */
    private fun kvaultAmount(data: ByteArray): BigInteger? {
        if (data.size < 16) return null
        var value = BigInteger.ZERO
        for (offset in 7 downTo 0) {
            value = value.shiftLeft(8).or(BigInteger.valueOf((data[8 + offset].toLong() and 0xFF)))
        }
        return value
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
