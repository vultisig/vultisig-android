package com.vultisig.wallet.data.blockchain.solana.kamino

/** One decoded instruction, reduced to what validation actually reasons about. */
data class KaminoTxInstruction(val programId: String, val data: ByteArray) {
    // ByteArray identity would make two structurally equal instructions compare unequal, which
    // matters because tests and set comparisons rely on value semantics here.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is KaminoTxInstruction &&
                programId == other.programId &&
                data.contentEquals(other.data))

    override fun hashCode(): Int = 31 * programId.hashCode() + data.contentHashCode()
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

    /**
     * @throws KaminoTransactionRejected if [instructions] is not a transaction the app is willing
     *   to sign for [vault].
     */
    fun validate(
        instructions: List<KaminoTxInstruction>,
        vault: KaminoVault,
        action: KaminoAction,
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
    }

    private fun reject(reason: String): Nothing = throw KaminoTransactionRejected(reason)
}
