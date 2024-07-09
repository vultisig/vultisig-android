package com.vultisig.wallet.data.models

internal data class DepositTransaction(
    val id: TransactionId,
    val vaultId: String,

    val srcAddress: String,
    val srcTokenValue: TokenValue,
    val memo: String,
    val dstAddress: String,
    val estimatedFees: TokenValue,
)
