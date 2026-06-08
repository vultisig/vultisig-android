package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

/**
 * A Bitcoin UTXO that can be claimed on the QBTC chain. [amount] is in satoshis; once the chain has
 * cross-checked the UTXO it reflects the chain's remaining `entitled_amount`, not the raw
 * Blockchair value. [confirmations] is the number of blocks mined since the UTXO was confirmed
 * (current tip − the UTXO's block height + 1); it is `null` for an unconfirmed (mempool) UTXO or
 * when the chain tip is unknown. It also gates claim maturity — see [isMature].
 */
data class ClaimableUtxo(
    val txid: String,
    val vout: Int,
    val amount: Long,
    val confirmations: Long? = null,
)

/**
 * True when the UTXO has enough confirmations to be claimable. The chain requires strictly more
 * than [QbtcClaimConfig.MIN_CLAIM_CONFIRMATIONS], so a `null` count (mempool / unknown tip) is
 * treated as immature — fail closed.
 */
internal fun ClaimableUtxo.isMature(): Boolean =
    (confirmations ?: 0L) > QbtcClaimConfig.MIN_CLAIM_CONFIRMATIONS
