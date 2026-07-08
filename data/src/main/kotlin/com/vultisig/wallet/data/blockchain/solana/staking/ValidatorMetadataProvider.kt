package com.vultisig.wallet.data.blockchain.solana.staking

/**
 * Swappable seam for off-chain validator enrichment (name / logo / APY / score). The core
 * stake/commission/activation data comes from the on-chain `getVoteAccounts` read (see
 * [SolanaStakingService]); this interface only supplies display metadata — the part most likely to
 * change source (Stakewiz today, validators.app or an in-house endpoint tomorrow). Keeping it
 * behind an interface lets the picker and view models stay independent of any single provider.
 * Mirrors the iOS metadata seam (vultisig-ios #4660).
 *
 * Contract: implementations MUST NOT throw on a provider outage. A failed or rate-limited source
 * returns a partial or empty map so callers degrade gracefully — falling back to a truncated vote
 * pubkey and the on-chain commission, with no logo, never a crash.
 */
interface ValidatorMetadataProvider {

    /**
     * Resolves [ValidatorMetadata] keyed by vote pubkey for the requested validators.
     *
     * The returned map may be empty or cover only a subset of the input — a validator absent from
     * the map simply has no enrichment available and the caller falls back to on-chain data. This
     * call never throws.
     */
    suspend fun metadata(votePubkeys: List<String>): Map<String, ValidatorMetadata>
}
