package com.vultisig.wallet.data.models

data class SignedTransactionResult(
    val rawTransaction: String,
    val transactionHash: String,
    val signature: String? = null,
)