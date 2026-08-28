package com.vultisig.wallet.data.api

import javax.inject.Inject
import wallet.core.jni.SolanaAddress

/**
 * Resolves the Jupiter platform-fee account. Jupiter charges 0% itself; the only fee is the
 * VULT-scaled affiliate fee we add, credited to a Vultisig-owned ATA — we keep 100% of it (no
 * Jupiter on-chain Referral Program).
 *
 * The fee ATA is provisioned OFF the signed path: we never inject a create-ATA instruction.
 * Wallet-core's `insertInstruction` appends accounts as new static keys without deduplicating
 * against address lookup tables, so a route that already ALT-loads one of them is rejected on-chain
 * with `AccountLoadedTwice`. If this probe throws (unprovisioned ATA, missing mint, RPC blip),
 * [JupiterApi] requotes without the affiliate fee so the swap still routes.
 *
 * Behind an interface so the wallet-core JNI + RPC work can be faked in unit tests.
 */
interface JupiterFeeAtaService {
    /**
     * Derive the Vultisig fee ATA for [feeMint] and verify it exists on-chain (read-only, off the
     * signed path). Throws on a missing/unsupported mint, RPC failure, or an unprovisioned fee ATA
     * so the caller can requote Jupiter without a platform fee.
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
        checkNotNull(solanaApi.getAccountOwner(feeAccount)) {
            "Jupiter fee ATA $feeAccount for mint $feeMint not provisioned"
        }
        return feeAccount
    }

    companion object {
        const val JUPITER_FEE_OWNER_ADDRESS = "8iqhrtBzMcYLR6c6FkzeoMHibedYDkHvLKnX2ArNie5z"
        const val TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val TOKEN_2022_PROGRAM_ID = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
    }
}
