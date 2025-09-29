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
    val tokenValue: TokenValue,
    val fiatValue: FiatValue,
    val gasFee: TokenValue,
    val totalGas: String,
    val memo: String?,
    val estimatedFee: String,
    val blockChainSpecific: BlockChainSpecific,
    val utxos: List<UtxoInfo> = emptyList(),
)