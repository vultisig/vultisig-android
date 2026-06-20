package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

/**
 * Single source of truth for QBTC claim constants. Mirrors the SDK, iOS, and Windows references —
 * these MUST stay aligned byte-for-byte with the chain (`x/qbtc/zk/message.go`) and the proof
 * service.
 */
object QbtcClaimConfig {
    /** Cosmos chain id, also hashed into the claim message hash. */
    const val CHAIN_ID = "qbtc"

    const val MAX_CLAIM_UTXOS = 50

    /**
     * The chain rejects a claim unless the funding UTXO has strictly more than this many
     * confirmations ("no valid claimable UTXOs found"), so the client filters immature UTXOs out.
     */
    const val MIN_CLAIM_CONFIRMATIONS = 144L

    /**
     * The proof service expects `signature_r` zero-padded to this byte width — 24, not 32, matching
     * the proof circuit's witness size.
     */
    const val PROOF_SERVICE_R_BYTES = 24

    /** The proof service expects `signature_s` zero-padded to 32 bytes. */
    const val PROOF_SERVICE_S_BYTES = 32

    /** Proof generation is a slow PLONK prover — allow up to 5 minutes. */
    const val PROOF_SERVICE_TIMEOUT_MS = 300_000L

    /** ASCII domain-separation tags for the claim message hash. */
    const val DOMAIN_TAG_PREFIX = "ecdsa-hash160:"
    const val DOMAIN_TAG_SUFFIX = "qbtc-claim-v1"

    /** The claim message hash uses the first 8 bytes of `SHA256(chainId)`. */
    const val CHAIN_ID_HASH_PREFIX_BYTES = 8
}
