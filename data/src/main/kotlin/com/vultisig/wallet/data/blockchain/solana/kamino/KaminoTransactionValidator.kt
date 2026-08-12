package com.vultisig.wallet.data.blockchain.solana.kamino

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

    /**
     * @throws KaminoTransactionRejected if [instructions] is not a transaction the app is willing
     *   to sign for [vault].
     */
    fun validate(
        instructions: List<KaminoTxInstruction>,
        vault: KaminoVault,
        action: KaminoAction,
        signerAddress: String? = null,
    ) {
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

        rejectValueMovementAwayFromTheSigner(instructions, vault, signerAddress)
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
        signerAddress: String?,
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
                    if (
                        discriminator == SYSTEM_TRANSFER &&
                            vault.tokenMint != KaminoVaultRegistry.WRAPPED_SOL_MINT
                    ) {
                        reject(
                            "instruction $index moves lamports, which only the wrapped-SOL vault " +
                                "has cause to do"
                        )
                    }
                }
            }
        }

        // The transaction must be the signer's own. Index 0 of a Solana message is the fee payer
        // and
        // first required signer, so anything else means signing on another account's behalf.
        if (signerAddress != null) {
            val feePayer = instructions.firstNotNullOfOrNull { it.accounts.firstOrNull() }
            if (feePayer != null && feePayer != signerAddress) {
                reject("transaction is authorised by $feePayer rather than the wallet")
            }
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
