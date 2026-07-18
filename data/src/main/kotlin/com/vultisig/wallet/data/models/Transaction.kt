package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo

typealias TransactionId = String

data class Transaction(
    val id: TransactionId,
    val vaultId: String,
    val chainId: String,
    val token: Coin,
    val srcAddress: String,
    val dstAddress: String,
    val dstLabel: String? = null,
    val tokenValue: TokenValue,
    val fiatValue: FiatValue,
    val gasFee: TokenValue,
    val totalGas: String,
    val memo: String?,
    val signAmino: String? = null,
    val signDirect: String? = null,
    /** Base64 raw txs of a dApp signSolana batch (issue #5238); one entry per transaction. */
    val signSolana: List<String> = emptyList(),
    /**
     * Base64 `TransactionData` BCS bytes of a dApp-supplied Sui PTB, surfaced for verify display.
     */
    val signSui: String? = null,
    /** Raw XRPL transaction JSON of a dApp-supplied `SignRipple`, surfaced for verify display. */
    val signRipple: String? = null,
    val estimatedFee: String,
    val blockChainSpecific: BlockChainSpecific,
    val utxos: List<UtxoInfo> = emptyList(),
)
