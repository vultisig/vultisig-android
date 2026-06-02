package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

/**
 * A Bitcoin UTXO that can be claimed on the QBTC chain. [amount] is in satoshis; once the chain has
 * cross-checked the UTXO it reflects the chain's remaining `entitled_amount`, not the raw
 * Blockchair value. [confirmations] is the number of blocks mined since the UTXO was confirmed
 * (current tip − the UTXO's block height + 1), used only for display; it is `null` for an
 * unconfirmed (mempool) UTXO or when the chain tip is unknown.
 */
data class ClaimableUtxo(
    val txid: String,
    val vout: Int,
    val amount: Long,
    val confirmations: Long? = null,
)
