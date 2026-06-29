package com.vultisig.wallet.data.api

import javax.inject.Inject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import wallet.core.jni.SolanaAddress
import wallet.core.jni.SolanaTransaction

/**
 * Fee-mint metadata for a Jupiter affiliate fee: the Vultisig-owned Associated Token Account that
 * Jupiter credits the fee to, plus the token program that owns the mint (so the create instruction
 * and ATA derivation use the matching Token vs Token-2022 program).
 */
data class JupiterFeeAccount(
    val feeAccount: String,
    val mint: String,
    val owner: String,
    val tokenProgramId: String,
)

/**
 * Resolves and provisions the Jupiter platform-fee account. Jupiter charges 0% itself; the only fee
 * is the VULT-scaled affiliate fee we add, taken in the swap's output mint (the only mint allowed
 * on an ExactIn swap besides the input) and credited to a Vultisig-owned ATA — we keep 100% of it
 * (no Jupiter on-chain Referral Program). Jupiter's `/swap` auto-creates the *user's* output ATA
 * but never the fee ATA, so we must prepend our own idempotent create. Behind an interface so the
 * wallet-core JNI + RPC work can be faked in unit tests. Mirrors the SDK's `jupiterFeeAta.ts`.
 */
interface JupiterFeeAtaService {
    /**
     * Resolve the fee ATA + owning token program for [outputMint]. Throws on a missing/unsupported
     * mint or RPC failure so the caller fails the Jupiter quote (falling back to another provider)
     * rather than silently deriving the wrong ATA.
     */
    suspend fun resolveFeeAccount(outputMint: String): JupiterFeeAccount

    /**
     * Prepend an idempotent create-associated-token-account instruction for [fee] (payer =
     * [feePayer], the swap's fee payer) to the base64 [txData], returning the updated transaction.
     * The idempotent variant is a no-op (no rent) when the ATA already exists, so this is safe to
     * run on every swap without an existence probe.
     */
    fun prependCreateFeeAta(txData: String, fee: JupiterFeeAccount, feePayer: String): String
}

internal class JupiterFeeAtaServiceImpl @Inject constructor(private val solanaApi: SolanaApi) :
    JupiterFeeAtaService {

    override suspend fun resolveFeeAccount(outputMint: String): JupiterFeeAccount {
        val ownerProgram =
            solanaApi.getAccountOwner(outputMint)
                ?: error("Jupiter fee mint $outputMint not found; cannot resolve its token program")
        val tokenProgramId =
            when (ownerProgram) {
                TOKEN_PROGRAM_ID -> TOKEN_PROGRAM_ID
                TOKEN_2022_PROGRAM_ID -> TOKEN_2022_PROGRAM_ID
                else ->
                    error("Jupiter fee mint $outputMint owned by unsupported program $ownerProgram")
            }
        val owner = SolanaAddress(JUPITER_FEE_OWNER_ADDRESS)
        val feeAccount =
            if (tokenProgramId == TOKEN_2022_PROGRAM_ID) owner.token2022Address(outputMint)
            else owner.defaultTokenAddress(outputMint)
        require(!feeAccount.isNullOrEmpty()) {
            "Failed to derive the Jupiter fee ATA for $outputMint"
        }
        return JupiterFeeAccount(
            feeAccount = feeAccount,
            mint = outputMint,
            owner = JUPITER_FEE_OWNER_ADDRESS,
            tokenProgramId = tokenProgramId,
        )
    }

    override fun prependCreateFeeAta(
        txData: String,
        fee: JupiterFeeAccount,
        feePayer: String,
    ): String =
        SolanaTransaction.insertInstruction(
            txData,
            FEE_ATA_INSTRUCTION_INDEX,
            createIdempotentAtaInstructionJson(
                feePayer = feePayer,
                ata = fee.feeAccount,
                owner = fee.owner,
                mint = fee.mint,
                tokenProgramId = fee.tokenProgramId,
            ),
        ) ?: error("Failed to insert the Jupiter fee-ATA create instruction")

    companion object {
        const val JUPITER_FEE_OWNER_ADDRESS = "8iqhrtBzMcYLR6c6FkzeoMHibedYDkHvLKnX2ArNie5z"
        const val TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val TOKEN_2022_PROGRAM_ID = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"

        // Prepend ahead of every program instruction; the create-ATA must run before Jupiter's swap
        // transfers the fee into the account.
        private const val FEE_ATA_INSTRUCTION_INDEX = 0
    }
}

private const val ASSOCIATED_TOKEN_PROGRAM_ID = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
private const val SYSTEM_PROGRAM_ID = "11111111111111111111111111111111"

// Base58 of the single instruction byte [1] — the SPL ATA program's CreateIdempotent discriminator.
private const val CREATE_IDEMPOTENT_DATA_BASE58 = "2"

/**
 * Build the wallet-core `SolanaTransaction.insertInstruction` JSON for an idempotent
 * create-associated-token-account instruction. Account order and signer/writable flags follow the
 * SPL Associated Token Account program: payer (signer, writable), ata (writable), owner, mint,
 * system program, token program; data is the single-byte CreateIdempotent discriminator.
 */
internal fun createIdempotentAtaInstructionJson(
    feePayer: String,
    ata: String,
    owner: String,
    mint: String,
    tokenProgramId: String,
): String {
    fun meta(pubkey: String, isSigner: Boolean, isWritable: Boolean) = buildJsonObject {
        put("pubkey", pubkey)
        put("isSigner", isSigner)
        put("isWritable", isWritable)
    }
    return buildJsonObject {
            put("programId", ASSOCIATED_TOKEN_PROGRAM_ID)
            putJsonArray("accounts") {
                add(meta(feePayer, isSigner = true, isWritable = true))
                add(meta(ata, isSigner = false, isWritable = true))
                add(meta(owner, isSigner = false, isWritable = false))
                add(meta(mint, isSigner = false, isWritable = false))
                add(meta(SYSTEM_PROGRAM_ID, isSigner = false, isWritable = false))
                add(meta(tokenProgramId, isSigner = false, isWritable = false))
            }
            put("data", CREATE_IDEMPOTENT_DATA_BASE58)
        }
        .toString()
}
