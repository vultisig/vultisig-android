package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

/**
 * A Bitcoin UTXO that can be claimed on the QBTC chain. [amount] is in satoshis; once the chain has
 * cross-checked the UTXO it reflects the chain's remaining `entitled_amount`, not the raw
 * Blockchair value. [blockHeight] is `null` for an unconfirmed (mempool) UTXO.
 */
data class ClaimableUtxo(
    val txid: String,
    val vout: Int,
    val amount: Long,
    val blockHeight: Long? = null,
)
