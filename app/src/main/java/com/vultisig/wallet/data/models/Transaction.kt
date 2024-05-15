package com.vultisig.wallet.data.models

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
)