package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo

typealias TransactionId = String

internal data class Transaction(
    val id: TransactionId,
    val vaultId: String,
    val chainId: String,
    val tokenId: String,
    val srcAddress: String,
    val dstAddress: String,
    val tokenValue: TokenValue,
    val fiatValue: FiatValue,
    val gasFee: TokenValue,
    val memo: String?,

    val blockChainSpecific: BlockChainSpecific,
    val utxos: List<UtxoInfo> = emptyList(),
)