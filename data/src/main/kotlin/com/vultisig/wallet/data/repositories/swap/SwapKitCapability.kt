package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidChainIdentifier

/**
 * Which chains SwapKit may be *offered* on, independent of any per-chain allowlist. Port of iOS'
 * `SwapKitCapability`.
 *
 * The point of this object is that adding a chain to the wallet must not also require adding a row
 * to a SwapKit table before a quote is even attempted. [SwapProviderTableImpl] therefore stopped
 * naming `SWAPKIT` per chain and asks [canReceiveOn] instead; an EVM network is in as soon as
 * [SwapKitAssetPrefix] knows how SwapKit spells it, which is the same entry HyperEVM (999) and
 * Robinhood (4663) needed to be quotable at all.
 *
 * Two predicates, deliberately asymmetric:
 * - [canReceiveOn] — the app can hold a destination asset here. Exact token support is still the
 *   live SwapKit catalogue's call, negotiated at `/v3/quote`.
 * - [canQuoteFrom] — directional. An EVM SwapKit transaction is reputation-checked before signing,
 *   so only chains the security scanner covers may *originate* a route.
 *
 * The third arm of iOS' capability check — the `/v3/swap` payload matching the source chain's
 * implemented signer — lives in [SwapKitQuoteSource] instead, next to the tx-kind enum it inspects.
 */
internal object SwapKitCapability {

    /**
     * Non-EVM chains the app can hold a SwapKit destination asset on. Unlike the EVM side this
     * cannot be a property — each entry exists because a specific signer or deposit path was built
     * for it, named below. Mirrors iOS' list exactly.
     */
    private val NON_EVM_RECEIVE_CHAINS =
        setOf(
            // Segwit PSBT via SwapKitBtcSigner; Litecoin's addresses are P2WPKH / P2SH-P2WPKH so it
            // rides the same signer.
            Chain.Bitcoin,
            Chain.Litecoin,
            // Legacy P2PKH PSBT via SwapKitLegacyP2PKHSigner, each on its own WalletCore coin type.
            // BCH adds SIGHASH_FORKID natively.
            Chain.BitcoinCash,
            Chain.Dash,
            Chain.Dogecoin,
            // Sapling-v4 transparent PSBT, ZIP-243 sighash, via SwapKitZcashSigner. Transparent
            // only — Vultisig cannot manage shielded keys.
            Chain.Zcash,
            // Pre-built hex CBOR via SwapKitCardanoSigner (Blake2b-256 of the tx body, Ed25519 vkey
            // witness); the deposit-only variant is rebuilt as a plain send by CardanoHelper.
            Chain.Cardano,
            // Deposit-only: no signer. SwapKit returns a per-route deposit r-address and
            // RippleHelper builds a plain Payment to it.
            Chain.Ripple,
            Chain.Solana,
            // Blake2b-32 of the intent-prefixed PTB, Ed25519 envelope, via SwapKitSuiSigner.
            Chain.Sui,
            // Plain native deposit transfer signed through TonHelper.
            Chain.Ton,
            // TronWeb object → sha256 of raw_data_hex, via SwapKitTronSigner.
            Chain.Tron,
        )

    /**
     * True when the app can hold a SwapKit destination asset on [chain].
     *
     * Every EVM chain qualifies except [Chain.Sei], which the wallet holds but does not swap on at
     * all (it is the one EVM chain absent from iOS' `isSwapAvailable`), and those
     * [SwapKitAssetPrefix] has no spelling for — without a prefix the quote cannot even be
     * addressed, so offering SwapKit there only costs the pair its immediate "no route" guidance
     * and replaces it with a guaranteed failure once an amount is typed. Nothing narrower than
     * that: a chain SwapKit does not currently route is dropped later by the `/providers` gate in
     * [SwapKitQuoteSource], and an asset SwapKit does not list is dropped by `/v3/quote`.
     */
    fun canReceiveOn(chain: Chain): Boolean =
        SwapKitAssetPrefix.of(chain) != null &&
            if (chain.standard == TokenStandard.EVM) chain != Chain.Sei
            else chain in NON_EVM_RECEIVE_CHAINS

    /**
     * True when a SwapKit route may be *sourced* from [chain].
     *
     * Source-side eligibility is directional. An EVM SwapKit route signs calldata against a router
     * the app did not build, so it must be reputation-checked first; a chain the security scanner
     * cannot scan may still be a destination but never an origin. Robinhood is the live case —
     * Blockaid does not index 4663 — so it stays SwapKit destination-only rather than losing the
     * pair entirely.
     */
    fun canQuoteFrom(chain: Chain): Boolean =
        canReceiveOn(chain) &&
            (chain.standard != TokenStandard.EVM || BlockaidChainIdentifier.name(chain) != null)
}
