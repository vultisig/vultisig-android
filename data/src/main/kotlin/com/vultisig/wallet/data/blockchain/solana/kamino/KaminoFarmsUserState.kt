package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.crypto.Base58Codec
import com.vultisig.wallet.data.crypto.SolanaProgramDerivedAddress

/**
 * The farms program's per-user account: what decides *which* stake a farms instruction moves.
 *
 * Every farms instruction a deposit or withdraw carries names it, and its address is a program
 * address over the farm and the owner — so recomputing it locally binds an instruction to one farm
 * and one wallet at once. That is the only offline way to do it here: the farm account itself is an
 * address-lookup-table entry in every captured response and this decode resolves no tables, while
 * the user state is a static key in both captured deposits, in the as-built and the
 * compute-budget-injected forms alike.
 *
 * Without it a farms instruction is bound to nothing but its authority, and the response chooses
 * the farm — so `farms::stake` would sweep the wallet's whole share balance into a farm the
 * response made, on the signature the message already carries. iOS derives the same address, for
 * the same reason: `KaminoSolanaInstructions.swift:221`.
 */
object KaminoFarmsUserState {

    /** The farms program's own seed prefix for this account. */
    private val SEED = "user".toByteArray(Charsets.US_ASCII)

    /**
     * [owner]'s state in [farm], or null when either address cannot be read as a public key — which
     * the validator treats as a refusal rather than as a check it may skip.
     */
    fun derive(farm: String, owner: String): String? {
        val farmKey = publicKey(farm) ?: return null
        val ownerKey = publicKey(owner) ?: return null
        return SolanaProgramDerivedAddress.find(
            seeds = listOf(SEED, farmKey, ownerKey),
            programId = KaminoVaultRegistry.FARMS_PROGRAM_ID,
        )
    }

    private fun publicKey(address: String): ByteArray? =
        Base58Codec.decode(address)?.takeIf { it.size == PUBLIC_KEY_LENGTH }

    private const val PUBLIC_KEY_LENGTH = 32
}
