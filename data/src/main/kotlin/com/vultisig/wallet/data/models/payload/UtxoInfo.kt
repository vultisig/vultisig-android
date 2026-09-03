package com.vultisig.wallet.data.models.payload

import java.math.BigInteger

/**
 * A Cardano native asset carried by a single UTxO, as it crosses the keysign wire
 * (`vultisig.keysign.v1.CardanoTokenAsset`).
 *
 * Only the initiator queries Koios; every co-signer reads these values verbatim off the payload, so
 * the two devices feed WalletCore identical inputs and derive the same sighash. Both hex fields are
 * lowercase — the wire carries the canonical form so no peer has to normalize.
 */
data class CardanoTokenAsset(
    val policyId: String,
    val assetNameHex: String,
    val amount: BigInteger,
)

data class UtxoInfo(
    val hash: String,
    val amount: Long,
    val index: UInt,
    /**
     * Cardano-only: the native assets this UTxO carries. Empty for every other UTXO chain and for
     * ADA-only UTxOs.
     *
     * Cardano's ledger conserves value per asset, so a transaction that spends a token-bearing
     * input has to declare those tokens or the body is rejected. WalletCore can only do that if the
     * input says what it holds.
     */
    val cardanoTokens: List<CardanoTokenAsset> = emptyList(),
)
