package com.vultisig.wallet.data.blockchain.utxo

import com.vultisig.wallet.data.models.payload.UtxoInfo

/**
 * What one transaction this wallet broadcast did to the sending address's UTXO set: the outpoints
 * it consumed and the outputs it paid back to that same address (change, or a self-send).
 *
 * Recorded at broadcast and read back by [SpendableUtxos] so the wallet's own recent spending is
 * applied on top of whatever snapshot the provider hands back. Blockchair serves the address
 * dashboard from a 60–120 s cache and its mempool view lags behind our broadcast, so for a couple
 * of minutes after every send the provider still lists the inputs that send consumed and not yet
 * the change it created. Fed that snapshot verbatim, the next send picks an input the network
 * already saw spent and dies at broadcast with `bad-txns-inputs-missingorspent` (#5867).
 */
data class UtxoInFlightTx(
    val txHash: String,
    /** Wall-clock millis at broadcast; the reconciliation replays transactions in this order. */
    val broadcastAt: Long,
    /** Inputs the transaction spent. */
    val spent: List<UtxoInfo>,
    /** Outputs paid back to the sending address, keyed by this transaction's hash. */
    val created: List<UtxoInfo>,
)
