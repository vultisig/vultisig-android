package com.vultisig.wallet.data.api

import javax.inject.Inject
import wallet.core.jni.SolanaAddress

/**
 * Resolves the Jupiter platform-fee account. Jupiter charges 0% itself; the only fee is the
 * VULT-scaled affiliate fee we add, credited to a Vultisig-owned ATA — we keep 100% of it (no
 * Jupiter on-chain Referral Program).
 *
 * The fee ATA is provisioned OFF the signed path (a backend responsibility): we never build or
 * inject a create-ATA instruction. Wallet-core's `insertInstruction` appends the instruction's
 * accounts as new static keys without deduplicating against the transaction's address lookup
 * tables, so any route that already ALT-loads one of them (e.g. the wSOL mint on a SOL-output swap)
 * is rejected on-chain with `AccountLoadedTwice`. If the fee ATA isn't provisioned yet, the quote
 * throws and Jupiter is dropped for the pair, letting another provider (LiFi, which also collects
 * the affiliate fee) serve it. Mirrors the iOS `JupiterService` fee handling.
 *
 * Behind an interface so the wallet-core JNI + RPC work can be faked in unit tests.
 */
interface JupiterFeeAtaService {
    /**
     * Derive the Vultisig fee ATA for [feeMint] and verify it exists on-chain (read-only, off the
     * signed path). Throws on a missing/unsupported mint, RPC failure, or an unprovisioned fee ATA
     * so the caller fails the Jupiter quote (falling back to another provider) rather than signing
     * a swap whose fee cannot be collected.
     */
    suspend fun resolveFeeAccount(feeMint: String): String
}

internal class JupiterFeeAtaServiceImpl @Inject constructor(private val solanaApi: SolanaApi) :
    JupiterFeeAtaService {

    override suspend fun resolveFeeAccount(feeMint: String): String {
        val ownerProgram =
            solanaApi.getAccountOwner(feeMint)
                ?: error("Jupiter fee mint $feeMint not found; cannot resolve its token program")
        val tokenProgramId =
            when (ownerProgram) {
                TOKEN_PROGRAM_ID -> TOKEN_PROGRAM_ID
                TOKEN_2022_PROGRAM_ID -> TOKEN_2022_PROGRAM_ID
                else ->
                    error("Jupiter fee mint $feeMint owned by unsupported program $ownerProgram")
            }
        val owner = SolanaAddress(JUPITER_FEE_OWNER_ADDRESS)
        val feeAccount =
            if (tokenProgramId == TOKEN_2022_PROGRAM_ID) owner.token2022Address(feeMint)
            else owner.defaultTokenAddress(feeMint)
        require(!feeAccount.isNullOrEmpty()) { "Failed to derive the Jupiter fee ATA for $feeMint" }
        // Never route to Jupiter unless the fee can be collected into an already-provisioned ATA.
        // A transient probe failure also drops Jupiter here — conservative, since another provider
        // still serves the pair.
        checkNotNull(solanaApi.getAccountOwner(feeAccount)) {
            "Jupiter fee ATA $feeAccount for mint $feeMint not provisioned; dropping Jupiter"
        }
        return feeAccount
    }

    companion object {
        const val JUPITER_FEE_OWNER_ADDRESS = "8iqhrtBzMcYLR6c6FkzeoMHibedYDkHvLKnX2ArNie5z"
        const val TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val TOKEN_2022_PROGRAM_ID = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
    }
}
